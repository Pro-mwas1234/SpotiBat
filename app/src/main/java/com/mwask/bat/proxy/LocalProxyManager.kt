package com.project.lol.proxy

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

object LocalProxyManager {
    private const val TAG = "LocalProxy"
    private const val KEYSTORE_FILE = "proxy_ca.p12"
    private const val KEYSTORE_PREFS = "spotilol_secure_prefs"
    private const val KEY_PASSWORD = "keystore_password"
    private const val CA_ALIAS = "spotilol-ca"
    private const val KEYSTORE_TYPE = "PKCS12"

    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var acceptorFuture: Future<*>? = null
    @Volatile private var caKeyPair: KeyPair? = null
    @Volatile private var caCert: X509Certificate? = null
    @Volatile private var leafKeyPair: KeyPair? = null
    @Volatile private var threadPool: ExecutorService? = null
    @Volatile private var pipeExecutor: ExecutorService? = null
    @Volatile private var acceptorExecutor: ExecutorService? = null

    private val sslContextCache = Collections.synchronizedMap(HashMap<String, SSLContext>())

    // A new CONNECT tunnel to a host we talked to recently can skip the TCP+TLS handshake
    // by using Idle upstream TLS sockets.
    private const val MAX_IDLE_UPSTREAM_PER_HOST = 4
    private val upstreamPool = ConcurrentHashMap<String, ConcurrentLinkedQueue<SSLSocket>>()

    val port: Int get() = serverSocket?.localPort ?: 0
    val isRunning: Boolean get() = serverSocket?.isBound == true && !(serverSocket?.isClosed ?: true)

    private fun getOrCreateKeystorePassword(context: Context): String {
        val prefs = securePrefs(context)
        var password = try {
            prefs.getString(KEY_PASSWORD, null)
        } catch (_: Exception) {
            null
        }
        if (password == null) {
            val random = SecureRandom()
            val bytes = ByteArray(32)
            random.nextBytes(bytes)
            password = Base64.encodeToString(bytes, Base64.NO_WRAP)
            try {
                prefs.edit { putString(KEY_PASSWORD, password) }
            } catch (_: Exception) {}
        }
        return password
    }

    private fun securePrefs(context: Context): SharedPreferences {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            return EncryptedSharedPreferences.create(
                KEYSTORE_PREFS,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            try {
                val ks = KeyStore.getInstance("AndroidKeyStore")
                ks.load(null)
                ks.deleteEntry(MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC))
            } catch (_: Exception) {}
            return try {
                val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                EncryptedSharedPreferences.create(
                    KEYSTORE_PREFS,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (_: Exception) {
                context.getSharedPreferences(KEYSTORE_PREFS, Context.MODE_PRIVATE)
            }
        }
    }

    fun init(context: Context) {
        val ksFile = File(context.filesDir, KEYSTORE_FILE)
        val password = getOrCreateKeystorePassword(context)

        if (!ksFile.exists()) {
            Log.d(TAG, "Generating new CA certificate")
            generateCA(ksFile, password)
        } else {
            try {
                Log.d(TAG, "Loading existing CA certificate")
                loadCA(ksFile, password)
            } catch (_: Exception) {
                try {
                    Log.d(TAG, "Migrating keystore to new password")
                    val ks = KeyStore.getInstance(KEYSTORE_TYPE)
                    ksFile.inputStream().use { ks.load(it, "".toCharArray()) }
                    val entry = ks.getEntry(CA_ALIAS, KeyStore.PasswordProtection("".toCharArray())) as KeyStore.PrivateKeyEntry
                    caKeyPair = KeyPair(entry.certificate.publicKey, entry.privateKey)
                    caCert = entry.certificate as X509Certificate
                    val newKs = KeyStore.getInstance(KEYSTORE_TYPE)
                    newKs.load(null, null)
                    newKs.setKeyEntry(CA_ALIAS, caKeyPair!!.private, password.toCharArray(), arrayOf(caCert))
                    ksFile.outputStream().use { newKs.store(it, password.toCharArray()) }
                    Log.d(TAG, "Keystore migrated to new password")
                } catch (e2: Exception) {
                    Log.w(TAG, "Failed to load/migrate CA, regenerating", e2)
                    ksFile.delete()
                    generateCA(ksFile, password)
                }
            }
        }
    }

    private inline fun <T> withUsLocale(block: () -> T): T {
        val previous = Locale.getDefault()
        return try {
            Locale.setDefault(Locale.US)
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    private fun generateCA(ksFile: File, password: String) {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        caKeyPair = kpg.generateKeyPair()

        val name = X500Name("CN=Spotilol Proxy CA, O=Spotilol")
        val serial = BigInteger.valueOf(System.currentTimeMillis())
        val notBefore = Date()
        val notAfter = Date(notBefore.time + 365L * 24 * 60 * 60 * 1000L * 10)

        val builder = withUsLocale {
            JcaX509v3CertificateBuilder(
                name, serial, notBefore, notAfter, name, caKeyPair!!.public
            )
        }

        builder.addExtension(
            Extension.basicConstraints,
            true,
            BasicConstraints(true)
        )

        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .build(caKeyPair!!.private)

        caCert = JcaX509CertificateConverter()
            .getCertificate(builder.build(signer))

        val ks = KeyStore.getInstance(KEYSTORE_TYPE)
        ks.load(null, null)
        ks.setKeyEntry(CA_ALIAS, caKeyPair!!.private, password.toCharArray(), arrayOf(caCert))
        ksFile.outputStream().use { ks.store(it, password.toCharArray()) }

        Log.d(TAG, "CA certificate generated and saved")
    }

    private fun loadCA(ksFile: File, password: String) {
        val ks = KeyStore.getInstance(KEYSTORE_TYPE)
        ksFile.inputStream().use { ks.load(it, password.toCharArray()) }

        val entry = ks.getEntry(CA_ALIAS, KeyStore.PasswordProtection(password.toCharArray())) as KeyStore.PrivateKeyEntry
        caKeyPair = KeyPair(entry.certificate.publicKey, entry.privateKey)
        caCert = entry.certificate as X509Certificate

        Log.d(TAG, "CA certificate loaded")
    }

    /**
     * Lazily (re)create the connection-handling pool so every start() gets a live one.
     */
    private fun pool(): ExecutorService {
        val p = threadPool
        if (p != null && !p.isShutdown) return p
        synchronized(this) {
            val p2 = threadPool
            if (p2 != null && !p2.isShutdown) return p2
            return Executors.newFixedThreadPool(32) { r -> Thread(r, "LocalProxy-Worker").apply { isDaemon = true }
            }.also { threadPool = it }
        }
    }

    /**
     * Cached thread pool for bidirectional pipe tasks and the CA trust probe.
     * Unbounded like raw Threads, but reuses idle threads instead of churning.
     * Idle threads are cleaned up after 60s.
     */
    private fun pipePool(): ExecutorService {
        val p = pipeExecutor
        if (p != null && !p.isShutdown) return p
        synchronized(this) {
            val p2 = pipeExecutor
            if (p2 != null && !p2.isShutdown) return p2
            return Executors.newCachedThreadPool { r ->
                Thread(r, "LocalProxy-Pipe").apply { isDaemon = true }
            }.also { pipeExecutor = it }
        }
    }

    /**
     * Single-thread executor for the acceptor loop.
     * Replaces the raw acceptorThread - same semantics, managed lifecycle.
     */
    private fun acceptorPool(): ExecutorService {
        val p = acceptorExecutor
        if (p != null && !p.isShutdown) return p
        synchronized(this) {
            val p2 = acceptorExecutor
            if (p2 != null && !p2.isShutdown) return p2
            return Executors.newSingleThreadExecutor { r ->
                Thread(r, "LocalProxy-Acceptor").apply { isDaemon = true }
            }.also { acceptorExecutor = it }
        }
    }

    @Synchronized
    fun start() {
        if (acceptorFuture?.isDone == false) return
        acceptorFuture = acceptorPool().submit {
            try {
                val ss = ServerSocket(0, 128, java.net.InetAddress.getByName("127.0.0.1"))
                serverSocket = ss
                Log.d(TAG, "Proxy started on port ${ss.localPort} (dedicated acceptor)")

                while (!ss.isClosed) {
                    val client = try {
                        ss.accept()
                    } catch (_: Exception) {
                        break
                    }
                    pool().execute { handleConnection(client) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start proxy", e)
            }
        }
    }

    @Synchronized
    fun stop() {
        try {
            serverSocket?.close()
            Log.d(TAG, "Proxy stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping proxy", e)
        } finally {
            serverSocket = null
        }
        // Shut down all executors. Pending connections finish their current work;
        // idle workers exit. pool()/pipePool()/acceptorPool() rebuild on next start().
        try { threadPool?.shutdown() } catch (_: Exception) {}
        try { pipeExecutor?.shutdown() } catch (_: Exception) {}
        try { acceptorExecutor?.shutdownNow() } catch (_: Exception) {}
        acceptorFuture = null
        closeAllPooledUpstreamSockets()
    }

    private fun poolKey(host: String, port: Int) = "$host:$port"

    /** Hands back an idle previously-verified upstream socket for this host, if one exists. */
    private fun borrowUpstreamSocket(host: String, port: Int): SSLSocket? {
        val queue = upstreamPool[poolKey(host, port)] ?: return null
        while (true) {
            val sock = queue.poll() ?: return null
            if (sock.isClosed || !sock.isConnected || isSocketLikelyDead(sock)) {
                try { sock.close() } catch (_: Exception) {}
                continue
            }
            return sock
        }
    }

    /** Parks a still-usable upstream socket for reuse, or closes it if it can't be reused. */
    private fun releaseUpstreamSocket(host: String, port: Int, sock: SSLSocket, reusable: Boolean) {
        if (!reusable || sock.isClosed || !sock.isConnected) {
            try { sock.close() } catch (_: Exception) {}
            return
        }
        val queue = upstreamPool.computeIfAbsent(poolKey(host, port)) { ConcurrentLinkedQueue() }
        if (queue.size >= MAX_IDLE_UPSTREAM_PER_HOST) {
            try { sock.close() } catch (_: Exception) {}
            return
        }
        queue.offer(sock)
    }

    private fun closeAllPooledUpstreamSockets() {
        upstreamPool.values.forEach { queue ->
            var sock = queue.poll()
            while (sock != null) {
                try { sock.close() } catch (_: Exception) {}
                sock = queue.poll()
            }
        }
        upstreamPool.clear()
    }

    /**
     * Pre-check a pooled socket before writing anything to it.
     * Similar to how Chromium handles its socket pool.
     * If the peer already closed the connection, it returns EOF immediately;
     * If the connection is idle-but-alive, nothing arrives and it times out.
     */
    private fun isSocketLikelyDead(socket: SSLSocket): Boolean {
        val original = try { socket.soTimeout } catch (_: Exception) { return true }
        return try {
            socket.soTimeout = 1
            socket.inputStream.read()
            true
        } catch (_: java.net.SocketTimeoutException) {
            false // nothing waiting - healthy, idle connection, the common case
        } catch (_: Exception) {
            true // any other I/O error on a socket we haven't used yet - don't risk it
        } finally {
            try { socket.soTimeout = original } catch (_: Exception) {}
        }
    }

    /** Opens and fully verifies a brand-new upstream TLS connection. */
    private fun openUpstreamSocket(host: String, port: Int, useHttp2: Boolean): SSLSocket {
        val socket = (SSLSocketFactory.getDefault().createSocket(host, port) as SSLSocket).also {
            it.tcpNoDelay = true
            if (Build.VERSION.SDK_INT >= 29) {
                val params = it.sslParameters
                params.applicationProtocols = if (useHttp2) arrayOf("h2") else arrayOf("http/1.1")
                it.sslParameters = params
            }
        }

        socket.soTimeout = 30000
        socket.startHandshake()

        val hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
        if (!hostnameVerifier.verify(host, socket.session)) {
            Log.e(TAG, "SECURITY ALERT: Hostname verification failed for $host. Possible network attack.")
            throw SSLPeerUnverifiedException("Cannot verify hostname: $host")
        }

        return socket
    }

    private fun handleConnection(client: Socket) {
        client.soTimeout = 30000
        client.tcpNoDelay = true
        try {
            val requestLine = readLine(client.inputStream) ?: return

            if (requestLine.startsWith("CONNECT")) {
                val hostPort = requestLine.split(" ")[1]
                val host = hostPort.substringBefore(":")
                val targetPort = hostPort.substringAfter(":").toIntOrNull() ?: 443
                var line: String
                do {
                    line = readLine(client.inputStream) ?: break
                } while (line.isNotEmpty())
                handleConnect(client, host, targetPort)
            } else {
                Log.w(TAG, "Non-CONNECT request, ignoring: $requestLine")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection error", e)
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun handleConnect(client: Socket, host: String, targetPort: Int = 443) {
        Log.d(TAG, "CONNECT $host:$targetPort")

        var clientSSLSocket: SSLSocket? = null
        var upstreamSSLSocket: SSLSocket? = null
        // Optimistic default: only flip to false once we positively know the connection
        // can't be reused
        var reuseUpstream = true
        var fromPool = false

        try {
            client.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
            client.getOutputStream().flush()

            val sslContext = getOrCreateSSLContext(host)

            val clientSocket = sslContext.socketFactory.createSocket(
                client, host, client.port, true
            ) as SSLSocket
            clientSocket.useClientMode = false
            clientSSLSocket = clientSocket

            if (Build.VERSION.SDK_INT >= 29) {
                val params = clientSocket.sslParameters
                params.applicationProtocols = arrayOf("http/1.1")
                clientSocket.sslParameters = params
            }

            clientSocket.startHandshake()

            val negotiatedProtocol = if (Build.VERSION.SDK_INT >= 29) {
                clientSocket.applicationProtocol
            } else null

            // We only ever offer "http/1.1" to the client above, so this is always false currently.
            // Kept so upstream ALPN stays correct if that ever changes.
            val useHttp2 = negotiatedProtocol == "h2"
            Log.d(TAG, "Protocol for $host: ${negotiatedProtocol ?: "none"}")

            val borrowed = borrowUpstreamSocket(host, targetPort)
            var upstream: SSLSocket = if (borrowed != null) {
                fromPool = true
                Log.d(TAG, "Reusing pooled upstream connection for $host")
                borrowed
            } else {
                openUpstreamSocket(host, targetPort, useHttp2)
            }
            upstreamSSLSocket = upstream

            // FIX (perf): raw SSL streams cost one syscall per byte in readLine()'s
            // single-byte read loop. Buffering both sides turns every HTTP head
            // from ~150 syscalls into a handful. Existing flush discipline in
            // writeHead/pipeExactBytes/pipeChunkedBody already covers every
            // write path, so nothing stalls.
            val clientIn = BufferedInputStream(clientSocket.inputStream, 16384)
            val clientOut = BufferedOutputStream(clientSocket.outputStream, 16384)
            var upstreamIn = BufferedInputStream(upstream.inputStream, 16384)
            var upstreamOut = BufferedOutputStream(upstream.outputStream, 16384)

            if (useHttp2) {
                reuseUpstream = false
                bidirectionalPipe(clientIn, clientOut, upstreamIn, upstreamOut)
            } else {
                while (true) {
                    val reqHead = readHttpHead(clientIn, null) ?: break
                    val requestMethod = extractMethod(reqHead)

                    modifyRequestHeaders(reqHead)

                    try {
                        writeHead(reqHead, upstreamOut)
                    } catch (e: Exception) {
                        //Retry once on a fresh socket instead of failing the whole tunnel;
                        if (!fromPool) throw e
                        Log.d(TAG, "Pooled upstream for $host was stale, reconnecting")
                        try { upstream.close() } catch (_: Exception) {}
                        upstream = openUpstreamSocket(host, targetPort, false)
                        upstreamSSLSocket = upstream
                        fromPool = false
                        upstreamIn = BufferedInputStream(upstream.inputStream, 16384)
                        upstreamOut = BufferedOutputStream(upstream.outputStream, 16384)
                        writeHead(reqHead, upstreamOut)
                    }
                    // outside the retry to reduce chance of truncated body
                    val clientBodyTruncated = pipeBody(clientIn, upstreamOut, reqHead, isResponse = false)
                    if (clientBodyTruncated) {
                        reuseUpstream = false
                        break
                    }

                    val respHead = readHttpHead(upstreamIn, requestMethod)
                    if (respHead == null) {
                        reuseUpstream = false
                        break
                    }

                    val statusCode = extractStatusCode(respHead)
                    writeHead(respHead, clientOut)

                    val upstreamExhausted = pipeBody(upstreamIn, clientOut, respHead, isResponse = true)
                    if (upstreamExhausted) {
                        // Body had no Content-Length/chunked framing, so it was read until
                        // EOF - the upstream socket is now dead regardless of what any
                        // Connection header said. End this tunnel cleanly rather than try
                        // to keep using (or pool) a socket that's already closed.
                        reuseUpstream = false
                        break
                    }

                    if (statusCode == 101) {
                        // FIX: 100 Continue is NOT a protocol switch - the real response
                        // follows it and must go through the normal framing path. Only
                        // 101 (Switching Protocols) upgrades to a raw tunnel.
                        clientSocket.soTimeout = 0
                        upstream.soTimeout = 0
                        reuseUpstream = false
                        bidirectionalPipe(clientIn, clientOut, upstreamIn, upstreamOut)
                        return
                    }

                    val reqConnection = getHeaderValue(reqHead, "Connection")
                    val respConnection = getHeaderValue(respHead, "Connection")

                    val keepAlive = !reqConnection.equals("close", ignoreCase = true) &&
                            !respConnection.equals("close", ignoreCase = true)

                    reuseUpstream = keepAlive
                    if (!keepAlive) break
                }
            }

        } catch (_: Exception) {
            reuseUpstream = false
        } finally {
            try { clientSSLSocket?.close() } catch (_: Exception) {}
            upstreamSSLSocket?.let { releaseUpstreamSocket(host, targetPort, it, reuseUpstream) }
        }
    }

    private data class HttpHead(
        val statusLine: String,
        val headers: MutableList<Pair<String, String>>,
        val contentLength: Long,
        val isChunked: Boolean,
        val noBody: Boolean
    )

    private fun readHttpHead(input: InputStream, methodHint: String?): HttpHead? {
        val statusLine = readLine(input) ?: return null
        if (statusLine.isEmpty()) return null

        val headers = mutableListOf<Pair<String, String>>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) {
                headers.add(line.substring(0, colon).trim() to line.substring(colon + 1).trim())
            }
        }

        val isResponse = statusLine.startsWith("HTTP/")
        val noBody: Boolean
        var contentLength = -1L
        var isChunked = false

        if (isResponse) {
            val code = extractStatusCodeFromLine(statusLine)
            noBody = code / 100 == 1 || code == 204 || code == 304 || methodHint.equals("HEAD", ignoreCase = true)
        } else {
            noBody = false
        }

        if (!noBody) {
            for ((k, v) in headers) {
                if (k.equals("Content-Length", ignoreCase = true)) {
                    contentLength = v.trim().toLongOrNull() ?: -1L
                }
                if (k.equals("Transfer-Encoding", ignoreCase = true) &&
                    v.equals("chunked", ignoreCase = true)) {
                    isChunked = true
                }
            }
        }

        return HttpHead(statusLine, headers, contentLength, isChunked, noBody)
    }
    private fun writeHead(head: HttpHead, output: OutputStream) {
        val sb = StringBuilder()
        sb.append(head.statusLine).append("\r\n")
        for ((k, v) in head.headers) sb.append(k).append(": ").append(v).append("\r\n")
        sb.append("\r\n")
        output.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        output.flush()
    }

    /**
     * Returns true if piping this body left the connection unusable (so the caller must
     * not pool it, and must treat this exchange as the last one on this socket).
     */
    private fun pipeBody(
        input: InputStream, output: OutputStream, head: HttpHead, isResponse: Boolean
    ): Boolean {
        return when {
            head.noBody -> false
            head.isChunked -> pipeChunkedBody(input, output)
            head.contentLength > 0 -> { pipeExactBytes(input, output, head.contentLength); false }
            isResponse && head.contentLength < 0 -> {
                // No Content-Length and not chunked on a RESPONSE: per RFC 7230 3.3.3 the
                // body is delimited by the connection closing, not a known length.
                // The old code did nothing here, read to EOF and forward it instead. This exhausts the upstream
                // socket by definition since it can only end via close.
                pipeUntilEof(input, output)
                true
            }
            else -> false
        }
    }

    private fun pipeUntilEof(input: InputStream, output: OutputStream) {
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n == -1) break
            output.write(buf, 0, n)
        }
        output.flush()
    }

    private fun pipeExactBytes(input: InputStream, output: OutputStream, count: Long) {
        val buf = ByteArray(32768)
        var remaining = count
        while (remaining > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (n == -1) throw java.io.EOFException("Unexpected end of stream")
            output.write(buf, 0, n)
            remaining -= n
        }
        output.flush()
    }

    private fun pipeChunkedBody(input: InputStream, output: OutputStream): Boolean {
        while (true) {
            val sizeLine = readLine(input) ?: return true
            if (sizeLine.isBlank()) continue

            output.write((sizeLine + "\r\n").toByteArray(Charsets.ISO_8859_1))
            val chunkSize = sizeLine.split(";")[0].trim().toLongOrNull(16) ?: return true

            if (chunkSize == 0L) {
                // Zero-or-more trailer header lines can follow the final chunk, terminated
                // by a blank line. Consume all of them so
                // nothing is left sitting unread on the socket.
                while (true) {
                    val trailerLine = readLine(input) ?: return true
                    output.write((trailerLine + "\r\n").toByteArray(Charsets.ISO_8859_1))
                    if (trailerLine.isEmpty()) break
                }
                output.flush()
                return false
            }

            pipeExactBytes(input, output, chunkSize)
            val crlf = readLine(input) ?: return true
            output.write((crlf + "\r\n").toByteArray(Charsets.ISO_8859_1))
        }
    }

    private fun extractMethod(head: HttpHead): String {
        val parts = head.statusLine.split(" ")
        return if (parts.isNotEmpty() && !parts[0].startsWith("HTTP")) parts[0] else ""
    }

    private fun extractStatusCode(head: HttpHead): Int {
        return extractStatusCodeFromLine(head.statusLine)
    }

    private fun extractStatusCodeFromLine(line: String): Int {
        return try {
            line.split(" ").getOrElse(1) { "" }.toInt()
        } catch (_: Exception) { -1 }
    }

    private fun getHeaderValue(head: HttpHead, name: String): String? {
        for ((k, v) in head.headers) {
            if (k.equals(name, ignoreCase = true)) return v
        }
        return null
    }

    private fun getOrCreateSSLContext(hostname: String): SSLContext {
        sslContextCache[hostname]?.let { return it }

        synchronized(this) {
            sslContextCache[hostname]?.let { return it }
            if (sslContextCache.size >= 128) sslContextCache.clear()

            val (domainCert, domainKeyPair) = generateDomainCert(hostname)

            val ks = KeyStore.getInstance(KEYSTORE_TYPE)
            ks.load(null, null)
            ks.setKeyEntry("leaf", domainKeyPair.private, "spotilol".toCharArray(), arrayOf(domainCert, caCert))

            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(ks, "spotilol".toCharArray())

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(kmf.keyManagers, null, SecureRandom())

            sslContextCache[hostname] = sslContext
            return sslContext
        }
    }

    private fun generateDomainCert(domain: String): Pair<X509Certificate, KeyPair> {
        // Reuse a single keypair for all leaf certificates to eliminate RSA overhead.
        // Thread-safe as only called within the synchronized block of getOrCreateSSLContext.
        if (leafKeyPair == null) {
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048, SecureRandom())
            leafKeyPair = kpg.generateKeyPair()
        }
        val domainKeyPair = leafKeyPair!!

        val issuer = X500Name("CN=Spotilol Proxy CA, O=Spotilol")
        val subject = X500Name("CN=$domain")
        val serial = BigInteger.valueOf(System.currentTimeMillis())
        val notBefore = Date()
        val notAfter = Date(notBefore.time + 365L * 24 * 60 * 60 * 1000L)

        val builder = withUsLocale {
            JcaX509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, subject, domainKeyPair.public
            )
        }

        val san = GeneralNames(GeneralName(GeneralName.dNSName, domain))
        builder.addExtension(Extension.subjectAlternativeName, false, san)

        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .build(caKeyPair!!.private)

        val cert = JcaX509CertificateConverter()
            .getCertificate(builder.build(signer))
        return Pair(cert, domainKeyPair)
    }

    private fun readLine(input: InputStream): String? {
        val baos = ByteArrayOutputStream()
        var prev = 0
        while (true) {
            val b = input.read()
            if (b == -1) return if (baos.size() == 0) null else baos.toString("ISO-8859-1")
            if (b == 10 && prev == 13) {
                val bytes = baos.toByteArray()
                return String(bytes, 0, bytes.size - 1, Charsets.ISO_8859_1)
            }
            baos.write(b)
            prev = b
        }
    }

    private fun modifyRequestHeaders(msg: HttpHead) {
        msg.headers.removeAll { it.first.equals("X-Requested-With", ignoreCase = true) }

        // Strip ALL sec-ch-ua-* headers - basic AND extended.
        // The basic 3 we replaced; the extended ones (full-version-list,
        // platform-version, arch, bitness, model) we just nuke. They
        // leak device model, ARM architecture, and Android version -
        // dead giveaway it's a mobile WebView, not a Windows desktop.
        msg.headers.removeAll { it.first.lowercase().startsWith("sec-ch-ua") }

        msg.headers.add("sec-ch-ua" to "\"Not;A=Brand\";v=\"8\", \"Chromium\";v=\"150\", \"Google Chrome\";v=\"150\"")
        msg.headers.add("sec-ch-ua-mobile" to "?0")
        msg.headers.add("sec-ch-ua-platform" to "\"Windows\"")
    }

    /**
     * Migrated from raw Threads to pipePool() with CountDownLatch.
     * Same semantics: two concurrent pipes, wait for both to finish.
     * The cached pool reuses idle threads instead of churning new ones
     * for every WebSocket upgrade / h2 tunnel.
     *
     * If pipePool() rejects a task (during shutdown), we count down
     * the latch ourselves so await() doesn't deadlock.
     */
    private fun bidirectionalPipe(
        clientIn: InputStream, clientOut: OutputStream,
        upstreamIn: InputStream, upstreamOut: OutputStream
    ) {
        val latch = CountDownLatch(2)

        fun submitPipe(input: InputStream, output: OutputStream) {
            try {
                pipePool().execute {
                    try {
                        val buf = ByteArray(8192)
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            output.write(buf, 0, n)
                            output.flush()
                        }
                    } catch (_: Exception) {}
                    try { output.close() } catch (_: Exception) {}
                    latch.countDown()
                }
            } catch (_: Exception) {
                try { output.close() } catch (_: Exception) {}
                latch.countDown()
            }
        }

        submitPipe(clientIn, upstreamOut)
        submitPipe(upstreamIn, clientOut)
        latch.await()
    }

    fun isCAInstalled(): Boolean {
        val ourCa = caCert ?: run {
            Log.e(TAG, "CA cert not loaded yet - cannot check installation")
            return false
        }

        try {
            val ourEncoded = ourCa.encoded
            val ks = KeyStore.getInstance("AndroidCAStore")
            ks.load(null, null)

            val aliases = ks.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                val cert = ks.getCertificate(alias) as? X509Certificate ?: continue
                try {
                    if (cert.encoded.contentEquals(ourEncoded)) {
                        Log.d(TAG, "CA found in trust store under alias: $alias")
                        return true
                    }
                } catch (_: Exception) {}
            }
            Log.d(TAG, "CA not found in Android trust store")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query AndroidCAStore", e)
            return false
        }
    }

    /**
     * FIX: MediaStore.Downloads rows from a previous installation survive
     * uninstallation (public Downloads = user files). After reinstall we
     * no longer own those rows - the old delete-by-name sweep threw
     * SecurityException and export failed forever. Now: per-row best-effort
     * cleanup, unique-name fallback, real filename reporting, app-dir
     * last resort, and no NPE when the CA isn't loaded.
     */
    fun exportCACert(context: Context): String {
        val ourCa = caCert ?: run {
            Log.e(TAG, "CA cert not loaded - cannot export")
            return "export failed"
        }
        val pem = try {
            val base64 = Base64.encodeToString(ourCa.encoded, Base64.DEFAULT or Base64.NO_WRAP)
            "-----BEGIN CERTIFICATE-----\n$base64\n-----END CERTIFICATE-----\n"
        } catch (_: Exception) {
            return "export failed"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver

            // Best-effort sweep. Orphaned rows throw SecurityException -
            // swallow PER-ROW and keep going; they must not abort the export.
            try {
                resolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                    arrayOf("Spotilol_CA.pem"),
                    null
                )?.use { cursor ->
                    val stale = mutableListOf<Long>()
                    while (cursor.moveToNext()) stale.add(cursor.getLong(0))
                    stale.forEach { id ->
                        try {
                            resolver.delete(
                                ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id),
                                null, null
                            )
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}

            var displayName = "Spotilol_CA.pem"
            var uri = try {
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, pendingValues(displayName))
            } catch (_: Exception) { null }

            if (uri == null) {
                // Canonical name is stuck behind an undeletable orphan -
                // export under a unique name instead of failing.
                displayName = "Spotilol_CA_${System.currentTimeMillis()}.pem"
                uri = try {
                    resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, pendingValues(displayName))
                } catch (_: Exception) { null }
            }

            if (uri == null) return exportToFileDir(context, pem)

            return try {
                val out = resolver.openOutputStream(uri)
                    ?: throw java.io.IOException("null output stream")
                out.use { it.write(pem.toByteArray()) }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)

                // MediaProvider may auto-rename on duplicate names - report
                // the REAL filename so the toast points at the right file.
                val realName = try {
                    resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                } catch (_: Exception) { null }
                val name = realName ?: displayName
                Log.d(TAG, "CA exported to Downloads as $name")
                File(File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOWNLOADS), name).absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "MediaStore export failed, falling back to app dir", e)
                try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                exportToFileDir(context, pem)
            }
        }

        return exportToFileDir(context, pem)
    }

    private fun pendingValues(name: String): ContentValues = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, name)
        put(MediaStore.Downloads.MIME_TYPE, "application/x-pem-file")
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        put(MediaStore.Downloads.IS_PENDING, 1)
    }

    /** Last resort: app-specific dir. No permissions needed on any API level. */
    private fun exportToFileDir(context: Context, pem: String): String {
        return try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val file = File(dir, "Spotilol_CA.pem")
            file.writeText(pem)
            Log.d(TAG, "CA exported to app dir: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export CA certificate", e)
            "export failed"
        }
    }

    /**
     * TRUE iff the system's DEFAULT trust pipeline accepts our CA *right now*.
     *
     * We serve a one-shot TLS endpoint signed by our CA on localhost, connect
     * with the platform-default SSLSocketFactory, and require the full
     * handshake + hostname verification to succeed. That exercises the real
     * trust decision (freshly constructed TrustManagerImpl), so it cannot be
     * fooled by stale keystore-enumeration views - the exact staleness that
     * made isCAInstalled() lie to us mid-session until an app restart.
     */
    fun isCATrustedLive(): Boolean {
        if (caCert == null) return false
        var listener: ServerSocket? = null
        var serverTls: SSLSocket? = null
        var accepted: Socket? = null
        var client: SSLSocket? = null

        return try {
            val ctx = getOrCreateSSLContext("localhost")

            listener = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
            val port = listener.localPort

            // Migrated from raw Thread to pipePool(). Fire-and-forget,
            // same as before - the finally block closes everything.
            try {
                pipePool().execute {
                    try {
                        accepted = listener.accept()
                        val tls = ctx.socketFactory.createSocket(
                            accepted, "localhost", port, true
                        ) as SSLSocket
                        serverTls = tls
                        tls.useClientMode = false
                        tls.startHandshake()
                        tls.outputStream.write(0x01)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            client = SSLSocketFactory.getDefault()
                .createSocket("127.0.0.1", port) as SSLSocket
            client.soTimeout = 3000
            client.startHandshake()

            HttpsURLConnection.getDefaultHostnameVerifier()
                .verify("localhost", client.session)

            true
        } catch (_: Exception) {
            false
        } finally {
            try { client?.close() } catch (_: Exception) {}
            try { serverTls?.close() } catch (_: Exception) {}
            try { accepted?.close() } catch (_: Exception) {}
            try { listener?.close() } catch (_: Exception) {}
        }
    }

    /** Combined verdict: fast path first, live handshake as the tiebreaker. */
    fun isCAEffectivelyInstalled(): Boolean =
        isCAInstalled() || isCATrustedLive()
}