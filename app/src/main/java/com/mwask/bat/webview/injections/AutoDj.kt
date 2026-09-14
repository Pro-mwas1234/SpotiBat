package com.mwask.bat.webview.injections

/*
 * CREDIT: SpotiBat - Smart DJ (modeled on Spotify's AI DJ).
 *
 * Like Spotify's real DJ, this builds a *lineup* from the user's taste
 * profile instead of picking from one static pool:
 *
 *   - Taste seeds: the user's top artists (short-term = current vibe,
 *     long-term = all-time/nostalgia) + liked songs, fetched once and
 *     refreshed periodically.
 *   - Lane rotation: each pick cycles familiar (current vibe artist)
 *     -> discovery (another artist from the pool) -> deep cut (liked song
 *     not heard recently) - the same blend of "favorites, fresh and
 *     nostalgic" Spotify describes.
 *   - Vibe switch: every 3 picks (a "couple of songs") the vibe artist
 *     rotates, and tapping the DJ chip switches it immediately.
 *   - Recently-played suppression: picks never repeat what was just heard,
 *     nor anything already played this session.
 *
 * Delivery is unchanged: the pick is queued via the connect-state command
 * endpoint so Spotify advances into it natively, with force-play fallbacks.
 *
 * Preference keys (spotiBat_prefs):
 *   DjMode : "disabled" | "enabled"
 *   DjLead : seconds before track end to act (10..30)
 */

object AutoDj {
    const val CONTENT = """
            window.__splDjVer = 2;

            /* ---------------- state ---------------- */
            window.__splDj = {
                enabled: window.__splDjEnabled === true,
                lead: window.__splDjLead || 15,
                /* lineup state */
                seeded: false,
                seededAt: 0,
                recentAt: 0,
                seedsVibe: [],      /* [{id,name}] short-term top artists */
                seedsRoot: [],      /* [{id,name}] long-term top artists */
                names: {},          /* artistId -> name cache */
                vibeArtistId: null,
                vibeArtistName: '',
                usedVibes: [],      /* recently used vibe artist ids */
                laneIdx: 0,         /* 0 familiar, 1 discovery, 2 deep cut */
                picksInVibe: 0,
                recent: {},         /* recently played track uri -> true */
                sessionUris: [],    /* everything queued this session */
                /* delivery state */
                lastTrackUri: null,
                firedFor: null,
                forceTimer: null,
                picking: false
            };

            window.splDjSetEnabled = function(on) {
                window.__splDj.enabled = !!on;
                /* defer the 3 seeding fetches until there is something to DJ
                   for: seeding at page load (idle player) competed with the
                   initial render. pickNextTrack/kickoff seed on demand. */
                if (on && !window.splTrackUri) { window.__splDj.seeded = false; djEnsureSeeded(); }
                try { AndBridge.dbg('s','[dj] ' + (on ? 'on' : 'off')); } catch(e){}
            };
            window.splDjSetLead = function(n) {
                var v = parseInt(n, 10);
                if (!isNaN(v)) window.__splDj.lead = Math.max(5, Math.min(30, v));
            };

            /* ---------------- helpers ---------------- */
            function djLog(msg) {
                try { AndBridge.dbg('s','[dj] ' + msg); } catch(e){}
            }

            function authHeaders() {
                var h = {
                    'Authorization': window.spotAuthToken,
                    'Content-Type': 'application/json;charset=UTF-8',
                    'app-platform': 'WebPlayer',
                    'spotify-app-version': '1.2.40.0'
                };
                if (window.spotCliToken) h['Client-Token'] = window.spotCliToken;
                return h;
            }

            async function apiGet(url) {
                await window.ensureAuthToken();
                var resp = await (window.mngFetch || oriFetch)(url, {
                    method: 'GET',
                    mode: 'cors',
                    credentials: 'include',
                    headers: authHeaders()
                });
                if (!resp.ok) throw new Error('http ' + resp.status);
                return await resp.json();
            }

            async function postJson(url, bodyObj) {
                await window.ensureAuthToken();
                var resp = await (window.mngFetch || oriFetch)(url, {
                    method: 'POST',
                    mode: 'cors',
                    credentials: 'include',
                    headers: authHeaders(),
                    body: JSON.stringify(bodyObj)
                });
                if (!resp.ok) throw new Error('http ' + resp.status);
                return await resp.json();
            }

            function gqlQuery(operationName, shaHash, variables) {
                return postJson('https://api-partner.spotify.com/pathfinder/v2/query', {
                    variables: variables || {},
                    operationName: operationName,
                    extensions: { persistedQuery: { version: 1, sha256Hash: shaHash } }
                });
            }

            function randomOf(arr) {
                return arr[Math.floor(Math.random() * arr.length)];
            }

            function nowPlayingArtistId() {
                try {
                    var a = document.querySelector('div[data-testid="now-playing-widget"] a[href*="/artist/"]');
                    if (a) {
                        var m = (a.getAttribute('href') || '').match(/\/artist\/([a-zA-Z0-9]{22})/);
                        if (m) return m[1];
                    }
                } catch(e){}
                return null;
            }

            /* ---------------- taste profile ---------------- */
            async function fetchTopArtists(timeRange, limit) {
                var data = await apiGet('https://api.spotify.com/v1/me/top/artists?limit=' + limit + '&time_range=' + timeRange);
                var items = (data && data.items) || [];
                var out = [];
                items.forEach(function(a) {
                    if (a && a.id && a.name) {
                        window.__splDj.names[a.id] = a.name;
                        out.push({ id: a.id, name: a.name });
                    }
                });
                return out;
            }

            async function refreshRecent() {
                try {
                    var data = await apiGet('https://api.spotify.com/v1/me/player/recently-played?limit=50');
                    var r = window.__splDj.recent;
                    ((data && data.items) || []).forEach(function(it) {
                        if (it && it.track && it.track.uri) r[it.track.uri] = true;
                    });
                    window.__splDj.recentAt = Date.now();
                } catch(e) {
                    djLog('recently-played unavailable: ' + e.message);
                }
            }

            function djEnsureSeeded() {
                var d = window.__splDj;
                if (d.seeded && Date.now() - d.seededAt < 30 * 60 * 1000) {
                    if (Date.now() - d.recentAt > 10 * 60 * 1000) refreshRecent();
                    return Promise.resolve();
                }
                if (!d.seedPromise) {
                    d.seedPromise = (async function() {
                        var vibe = [], roots = [];
                        try { vibe = await fetchTopArtists('short_term', 20); } catch(e) { djLog('top artists short: ' + e.message); }
                        try { roots = await fetchTopArtists('long_term', 30); } catch(e) { djLog('top artists long: ' + e.message); }
                        d.seedsVibe = vibe;
                        d.seedsRoot = roots;
                        /* de-dupe roots that are already in vibe */
                        d.seedsRoot = roots.filter(function(a) {
                            return !vibe.some(function(v) { return v.id === a.id; });
                        });
                        if (!d.vibeArtistId) {
                            var npId = nowPlayingArtistId();
                            var first = vibe[0] || roots[0] || (npId ? { id: npId, name: '' } : null);
                            if (first) {
                                d.vibeArtistId = first.id;
                                d.vibeArtistName = first.name || d.names[first.id] || '';
                            }
                        }
                        refreshRecent();
                        d.seeded = true;
                        d.seededAt = Date.now();
                        djLog('seeded: ' + vibe.length + ' vibe artists, ' + d.seedsRoot.length + ' root artists' + (d.vibeArtistName ? ', vibe=' + d.vibeArtistName : ''));
                    })().catch(function(e) {
                        djLog('seed failed: ' + e.message);
                    }).then(function() {
                        d.seedPromise = null;
                    });
                }
                return d.seedPromise;
            }

            /* ---------------- vibe switching ---------------- */
            window.splDjSwitchVibe = function(explicit) {
                var d = window.__splDj;
                var pool = d.seedsVibe.concat(d.seedsRoot).filter(function(a) {
                    return a.id !== d.vibeArtistId && d.usedVibes.indexOf(a.id) === -1;
                });
                if (!pool.length) {
                    /* everything used: recycle the full pool except current */
                    pool = d.seedsVibe.concat(d.seedsRoot).filter(function(a) { return a.id !== d.vibeArtistId; });
                    d.usedVibes = [];
                }
                if (!pool.length) {
                    if (explicit) AndBridge.deferMessage('DJ: no vibe pool yet');
                    return;
                }
                var next = randomOf(pool);
                d.vibeArtistId = next.id;
                d.vibeArtistName = next.name || d.names[next.id] || '';
                d.usedVibes.push(next.id);
                if (d.usedVibes.length > 8) d.usedVibes.shift();
                d.picksInVibe = 0;
                djLog('vibe -> ' + (d.vibeArtistName || next.id));
                if (explicit) AndBridge.deferMessage('DJ vibe: ' + (d.vibeArtistName || 'switched'));
            };

            /* ---------------- kickoff (first pick when idle) ---------------- */
            window.__splDj.kicking = false;
            window.splDjKickoff = async function() {
                var d = window.__splDj;
                if (!d.enabled || d.kicking) return;
                if (window.playing) { djLog('kickoff: already playing, loop has it'); return; }
                if (window.splTrackUri) { djLog('kickoff: paused mid-track, waiting for user'); return; }
                d.kicking = true;
                try {
                    await djEnsureSeeded();
                    var pick = await pickNextTrack();
                    if (!pick || !pick.uri) throw new Error('no pick');
                    await forcePlayTrack(pick.uri);
                    djLog('kickoff: playing ' + (pick.name || pick.uri));
                    try { AndBridge.deferMessage('DJ: ' + (pick.name || 'lineup started')); } catch(e){}
                } catch(e) {
                    djLog('kickoff failed: ' + e.message);
                    try { AndBridge.deferMessage('DJ kickoff failed'); } catch(e2){}
                } finally {
                    d.kicking = false;
                }
            };

            /* ---------------- track pools ---------------- */
            /* resolve tracks for an artist via artist overview (top tracks) */
            async function tracksForArtist(artistId) {
                var data = await gqlQuery('queryArtistOverview',
                    window.getGqlHash('queryArtistOverview', '5b9e64f43843fa3a9b6a98543600299b0a2cbbbccfdcdcef2402eb9c1017ca4c'),
                    { uri: 'spotify:artist:' + artistId, locale: '' });
                var artistData = data.data && (data.data.artistUnion || data.data.artist);
                if (artistData && artistData.profile && artistData.profile.name) {
                    window.__splDj.names[artistId] = artistData.profile.name;
                    if (window.__splDj.vibeArtistId === artistId) window.__splDj.vibeArtistName = artistData.profile.name;
                }
                var items = (artistData && (artistData.discography && artistData.discography.topTracks && artistData.discography.topTracks.items || artistData.topTracks && artistData.topTracks.items || artistData.topTracks)) || [];
                var out = [];
                items.forEach(function(elem) {
                    var t = (elem && (elem.track || (elem.item && elem.item.data) || elem)) || null;
                    var uri = t && (t.uri || t._uri);
                    var name = t && (t.name || t.title);
                    if (uri && uri.indexOf(':track:') !== -1) out.push({ uri: uri, name: name || 'track' });
                });
                if (!out.length) throw new Error('artist has no top tracks');
                return out;
            }

            async function getLikedTracks() {
                var tracks = [];
                try {
                    if (typeof window.getLikedSongsTracks === 'function') {
                        tracks = await window.getLikedSongsTracks();
                    } else if (window.likedSongsCache) {
                        tracks = window.likedSongsCache;
                    }
                } catch(e){}
                return (tracks || []).filter(function(t) {
                    return t && t.id && t.id.indexOf(':track:') !== -1;
                });
            }

            /* filter a track list: drop current, recently played and session repeats.
               relaxes progressively so we always return something if the pool has any. */
            function chooseTrack(list) {
                var d = window.__splDj;
                var cur = window.splTrackUri;
                var fresh = list.filter(function(t) {
                    return t.uri !== cur && !d.recent[t.uri] && d.sessionUris.indexOf(t.uri) === -1;
                });
                if (fresh.length) return randomOf(fresh);
                var semi = list.filter(function(t) { return t.uri !== cur && !d.recent[t.uri]; });
                if (semi.length) return randomOf(semi);
                var lastResort = list.filter(function(t) { return t.uri !== cur; });
                if (lastResort.length) return randomOf(lastResort);
                return list.length ? randomOf(list) : null;
            }

            /* ---------------- the three lanes ---------------- */
            async function pickFamiliar() {
                var d = window.__splDj;
                if (!d.vibeArtistId) throw new Error('no vibe artist');
                var tracks = await tracksForArtist(d.vibeArtistId);
                var pick = chooseTrack(tracks);
                if (!pick) throw new Error('familiar pool exhausted');
                return pick;
            }

            async function pickDiscovery() {
                var d = window.__splDj;
                var pool = d.seedsVibe.concat(d.seedsRoot).filter(function(a) { return a.id !== d.vibeArtistId; });
                if (!pool.length) throw new Error('no discovery pool');
                for (var attempt = 0; attempt < 3; attempt++) {
                    var artist = randomOf(pool);
                    try {
                        var tracks = await tracksForArtist(artist.id);
                        var pick = chooseTrack(tracks);
                        if (pick) return pick;
                    } catch(e) { /* try another artist */ }
                }
                throw new Error('discovery exhausted');
            }

            async function pickDeepCut() {
                var tracks = await getLikedTracks();
                if (!tracks.length) throw new Error('no liked songs');
                var mapped = tracks.map(function(t) { return { uri: t.id, name: t.name || 'liked song' }; });
                var pick = chooseTrack(mapped);
                if (!pick) throw new Error('deep cut pool exhausted');
                return pick;
            }

            async function pickNextTrack() {
                var d = window.__splDj;
                await djEnsureSeeded();

                /* rotate the vibe after every couple of songs, like the real DJ */
                if (d.picksInVibe >= 3) {
                    window.splDjSwitchVibe(false);
                }

                var lane = d.laneIdx % 3;
                var pick = null;
                var order = [pickFamiliar, pickDiscovery, pickDeepCut];
                /* try the scheduled lane first, then the others as fallback */
                for (var i = 0; i < 3 && !pick; i++) {
                    var fn = order[(lane + i) % 3];
                    try { pick = await fn(); } catch(e) {
                        djLog(fn.name + ' failed: ' + e.message);
                    }
                }
                if (!pick) throw new Error('all lanes exhausted');

                d.sessionUris.push(pick.uri);
                if (d.sessionUris.length > 40) d.sessionUris.shift();
                d.laneIdx++;
                d.picksInVibe++;
                var laneName = lane === 0 ? 'familiar' : lane === 1 ? 'discovery' : 'deep cut';
                djLog('pick [' + laneName + (d.vibeArtistName ? ' | ' + d.vibeArtistName : '') + ']: ' + (pick.name || pick.uri));
                return pick;
            }

            /* ---------------- delivery (queue + fallbacks) ---------------- */
            function commandEndpoint() {
                return 'https://gew4-spclient.spotify.com/connect-state/v1/player/command/from/' +
                    window.spotDevId + '/to/' + window.spotDevId;
            }

            async function queueTrack(uri) {
                await postJson(commandEndpoint(), {
                    command: {
                        context: { uri: uri, url: 'context://' + uri, metadata: {} },
                        play_origin: {
                            feature_identifier: 'your_library',
                            feature_version: featVer,
                            referrer_identifier: 'your_library'
                        },
                        options: { license: 'tft', skip_to: {}, player_options_override: {} },
                        endpoint: 'queue'
                    }
                });
            }

            async function forcePlayTrack(uri) {
                if (typeof window.playFromUri === 'function') {
                    window.playFromUri(uri, null);
                    return;
                }
                await postJson(commandEndpoint(), {
                    command: {
                        context: { uri: uri, url: 'context://' + uri, metadata: {} },
                        play_origin: {
                            feature_identifier: 'your_library',
                            feature_version: featVer,
                            referrer_identifier: 'your_library'
                        },
                        options: { license: 'tft', skip_to: {}, player_options_override: {} },
                        endpoint: 'play'
                    }
                });
            }

            /* returns { queued: true } on success, { queued: false } otherwise */
            async function tryQueueNext() {
                var d = window.__splDj;
                if (d.picking) return { queued: false };
                d.picking = true;
                try {
                    var pick = await pickNextTrack();
                    if (!pick || !pick.uri) throw new Error('no pick');
                    await queueTrack(pick.uri);
                    djLog('queued ' + (pick.name || pick.uri));
                    return { queued: true };
                } catch(e) {
                    djLog('queue failed: ' + e.message);
                    return { queued: false };
                } finally {
                    d.picking = false;
                }
            }

            function armForceFallback(secondsLeft, preEnd, extraDelaySec) {
                var d = window.__splDj;
                if (d.forceTimer) { clearTimeout(d.forceTimer); d.forceTimer = null; }
                var extra = extraDelaySec || 1;
                var wait = preEnd ? Math.max(0, secondsLeft - 2) * 1000 : (secondsLeft + extra) * 1000;
                d.forceTimer = setTimeout(async function() {
                    d.forceTimer = null;
                    if (!d.enabled) return;
                    var sameTrack = window.splTrackUri === d.firedFor;
                    var widgetGone = !window.splTrackUri;   /* player idle: bar unmounted */
                    if (!sameTrack && !widgetGone) return;  /* next track already rolled */
                    var dur = parseInt(typeof duration !== 'undefined' ? duration : 0, 10);
                    var pos = parseInt(typeof position !== 'undefined' ? position : 0, 10);
                    if (sameTrack && !preEnd && dur > 0 && pos < dur - 2) return; /* paused early */
                    var pick = null;
                    try { pick = await pickNextTrack(); } catch(e) { pick = null; }
                    if (!pick || !pick.uri) { djLog('force-play: no pick'); return; }
                    try {
                        await forcePlayTrack(pick.uri);
                        djLog('force-played ' + (pick.name || pick.uri));
                    } catch(e) {
                        djLog('force-play failed: ' + e.message);
                    }
                }, wait);
            }

            /* returns the next poll interval in ms (adaptive tick) */
            function djTick() {
                var d = window.__splDj;
                if (!d.enabled) return 3000;
                if (document.hidden) return 4000;            /* screen off/hidden tab */
                if (window.playing === false) return 4000;   /* paused: nothing to watch */
                var uri = window.splTrackUri;
                if (!uri || uri.indexOf(':track:') === -1) return 2000;   /* podcasts/videos: hands off */
                if (typeof duration === 'undefined' || typeof position === 'undefined') return 1000;
                var dur = parseInt(duration, 10);
                var pos = parseInt(position, 10);
                if (isNaN(dur) || isNaN(pos) || dur <= 0) return 1000;

                if (d.lastTrackUri !== uri) {
                    /* new track rolled in: reset per-track state */
                    d.lastTrackUri = uri;
                    d.firedFor = null;
                    if (d.forceTimer) { clearTimeout(d.forceTimer); d.forceTimer = null; }
                }

                var repMode = (typeof repmode !== 'undefined') ? repmode : 'false';
                var left = dur - pos;
                if (left > d.lead) {
                    if (d.firedFor) {
                        d.firedFor = null;
                        if (d.forceTimer) { clearTimeout(d.forceTimer); d.forceTimer = null; }
                    }
                    return 1000;
                }
                if (d.firedFor) return 1000;

                d.firedFor = uri;
                djLog('lead window: ' + left + 's left' + (repMode !== 'false' ? ' (repeat ' + repMode + ')' : ''));
                tryQueueNext().then(function(res) {
                    if (!res || !res.queued) {
                        armForceFallback(left, false, 1);
                    } else if (repMode === 'mixed') {
                        /* repeat-one loops the same track and never touches the queue */
                        armForceFallback(left, true, 1);
                    } else {
                        /* safety net: force-play if the queued track did not roll
                           within 5s after the end. no-ops when it worked. */
                        armForceFallback(left, false, 5);
                    }
                });
            }

            /* self-adjusting loop: djTick returns how long to wait next
               (1s while playing, 2-4s when idle/paused/hidden) */
            window.__splDjStop && window.__splDjStop();
            var djDelay = 1000;
            var djTimer = setTimeout(function djLoop() {
                try { djDelay = djTick() || 1000; } catch(e) { djDelay = 1000; }
                djTimer = setTimeout(djLoop, djDelay);
            }, 1000);
            window.__splDjStop = function() {
                clearTimeout(djTimer);
                var d = window.__splDj;
                if (d.forceTimer) { clearTimeout(d.forceTimer); d.forceTimer = null; }
            };

            /* seed on the first track we actually see, not at page load: the
               3 seeding fetches competed with the initial render. If a track
               is already up (session restore), seed right away - DJ will need
               it at the lead window. */
            try {
                if (window.__splDj.enabled && window.splTrackUri) djEnsureSeeded();
                else if (typeof window.splOnTrackChange === 'function') {
                    window.splOnTrackChange(function() {
                        if (window.__splDj.enabled && !window.__splDj.seeded) djEnsureSeeded();
                    });
                }
            } catch(e){}
    """
}
