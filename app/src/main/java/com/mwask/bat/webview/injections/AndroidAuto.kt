package com.project.lol.webview.injections

object AndroidAuto {
    const val CONTENT = """
            window.getGqlHash = function(opName, fb) {
                var h = window.opHash(opName, null);
                if (h) return h;
                try { var s = localStorage.getItem('sp_hash_' + opName); if (s) return s; } catch(e){}
                return fb;
            };

            window.ensureAuthToken = async function() {
                if (window.spotAuthToken) return true;
                var attempts = 0;
                while (!window.spotAuthToken && attempts < 10) {
                    await new Promise(function(r) { setTimeout(r, 100); });
                    attempts++;
                }
                return !!window.spotAuthToken;
            };

window.pendingMediaRequests = window.pendingMediaRequests || new Set();
            var isFetchingLib = false;
            var pendingLibPromise = null;

            window.checkMediaLib = async function(force) {
                if (isFetchingLib && !force) {
                    if (pendingLibPromise) return pendingLibPromise;
                    return;
                }
                if (pendingLibPromise && !force) return pendingLibPromise;
                await window.ensureAuthToken();
                if (!window.spotAuthToken) return;
                isFetchingLib = true;
                pendingLibPromise = (async function() {
                    try {
                        var allItems = await window.fetchAllLibrary();
                        var lib = window.parseLibrary(allItems);
                        ['playlists','albums','artists','podcasts'].forEach(function(k) {
                            (lib[k]||[]).forEach(function(it) { it.browsable = true; });
                        });
                        var pls = lib.playlists;
                        for (var i = 0; i < pls.length; i++) {
                            var p = pls[i];
                            if (p.name === 'Liked Songs' || (p.id||'').indexOf('collection') !== -1) {
                                p.id = 'spotify:collection:tracks';
                                p.image = 'https://misc.scdn.co/liked-songs/liked-songs-640.png';
                            }
                        }
                        window.mediaLib = lib;
                        if (window.pendingMediaRequests && window.pendingMediaRequests.size > 0) {
                            var it = window.pendingMediaRequests.values();
                            for (var reqId of it) {
                                var items = (window.mediaLib || {})[reqId] || [];
                                AndBridge.onMediaItemsLoaded(reqId, JSON.stringify(items));
                            }
                            window.pendingMediaRequests.clear();
                        }
                    } catch (e) {
                        console.error('checkMediaLib error:', e);
                    } finally {
                        isFetchingLib = false;
                        pendingLibPromise = null;
                    }
                })();
                return pendingLibPromise;
            };

            window.likedSongsCache = window.likedSongsCache || null;

            async function getLikedSongsTracks() {
                if (window.likedSongsCache && window.likedSongsCache.length > 0) {
                    return window.likedSongsCache;
                }
                var activeHash = window.getGqlHash('fetchLibraryTracks', '087278b20b743578a6262c2b0b4bcd20d879c503cc359a2285baf083ef944240');
                try {
                    await window.ensureAuthToken();
                    var headers = {
                        'Authorization': window.spotAuthToken,
                        'Content-Type': 'application/json;charset=UTF-8',
                        'app-platform': 'WebPlayer',
                        'spotify-app-version': '1.2.40.0'
                    };
                    if (window.spotCliToken) {
                        headers['Client-Token'] = window.spotCliToken;
                    }
                    var resp = await (window.mngFetch || oriFetch)('https://api-partner.spotify.com/pathfinder/v2/query', {
                        method: 'POST',
                        mode: 'cors',
                        credentials: 'include',
                        headers: headers,
                        body: JSON.stringify({
                            variables: { offset: 0, limit: 50 },
                            operationName: 'fetchLibraryTracks',
                            extensions: { persistedQuery: { version: 1, sha256Hash: activeHash } }
                        })
                    });
                    if (resp.ok) {
                        var data = await resp.json();
                        var tracksData = data.data && data.data.me && data.data.me.library && data.data.me.library.tracks;
                        if (!tracksData) tracksData = (data.data && data.data.libraryTracks) || data.data || null;
                        if (!tracksData) tracksData = (data.data && data.data.me && data.data.me.libraryV3) || null;
                        var items = (tracksData && tracksData.items) || [];
                        var tracks = items.map(function(elem) {
                            var trackWrapper = elem.track || elem.itemV2 || elem;
                            var trackData = trackWrapper.data || trackWrapper;
                            if (!trackData) return null;
                            var uri = trackWrapper._uri || trackWrapper.uri || trackData.uri || (trackData.id ? 'spotify:track:' + trackData.id : '') || '';
                            if (!uri || uri.indexOf(':track:') === -1) return null;
                            var artists = (trackData.artists && trackData.artists.items) ? trackData.artists.items.map(function(a) { return a.profile && a.profile.name; }).filter(Boolean) : (trackData.artists || []).map(function(a) { return a.name; }).filter(Boolean);
                            var albumData = trackData.albumOfTrack || trackData.album || {};
                            var coverUrl = (albumData.coverArt && albumData.coverArt.sources && albumData.coverArt.sources[0] && albumData.coverArt.sources[0].url) || (albumData.images && albumData.images[0] && albumData.images[0].url) || null;
                            return { id: uri, name: trackData.name || trackData.title || 'Unknown Track', image: coverUrl, artists: artists, browsable: false };
                        }).filter(Boolean);
                        if (tracks.length > 0) {
                            window.likedSongsCache = tracks;
                            return tracks;
                        }
                    }
                } catch (e) {
                    console.error('fetchLibraryTracks failed:', e);
                }
                return window.likedSongsCache || [];
            }

            try {
                (function() {
                    var uBtn = document.querySelector('a[data-testid="user-widget-link"]');
                    if (uBtn) {
                        var href = uBtn.getAttribute('href') || '';
                        var m = href.match(/\/user\/([^/?]+)/);
                        if (m && m[1] && m[1] !== 'collection') {
                            window.spotUserId = m[1];
                        }
                    }
                })();
            } catch(e){}

            async function fetchMediaItems(parentId) {
                try {
                    await window.ensureAuthToken();
                    if (parentId === 'playlists' || parentId === 'albums' || parentId === 'artists' || parentId === 'podcasts') {
                        if (!window.mediaLib || !window.mediaLib[parentId] || !window.mediaLib[parentId].length) {
                            window.pendingMediaRequests.add(parentId);
                            await window.checkMediaLib();
                        }
                        var items = ((window.mediaLib && window.mediaLib[parentId]) || []).map(function(item) {
                            var img = item.image;
                            if (item.name === 'Liked Songs' || (item.id && (item.id.indexOf('collection') !== -1 || item.id === 'spotify:playlist:liked'))) {
                                img = 'https://misc.scdn.co/liked-songs/liked-songs-640.png';
                            }
                            return { id: item.id, name: item.name, image: img, artists: item.artists || [], browsable: true, isGrid: true };
                        });
                        window.pendingMediaRequests.delete(parentId);
                        AndBridge.onMediaItemsLoaded(parentId, JSON.stringify(items));
                        return;
                    }

                    if (parentId === 'spotify:collection:tracks' || parentId.indexOf(':collection') !== -1 || parentId.indexOf('spotify:collection') === 0) {
                        var tracks = await getLikedSongsTracks();
                        AndBridge.onMediaItemsLoaded(parentId, JSON.stringify(tracks));
                        return;
                    }

                    if (parentId.indexOf('spotify:playlist:') === 0 || (parentId.indexOf(':user:') !== -1 && parentId.indexOf(':playlist:') !== -1)) {
                        var resp = await (window.mngFetch || oriFetch)('https://api-partner.spotify.com/pathfinder/v2/query', {
                            method: 'POST',
                            headers: {
                                'Authorization': window.spotAuthToken,
                                'Client-Token': window.spotCliToken,
                                'Content-Type': 'application/json;charset=UTF-8',
                                'app-platform': 'WebPlayer',
                                'spotify-app-version': '1.2.40.0'
                            },
                            body: JSON.stringify({
                                variables: { uri: parentId, offset: 0, limit: 100, enableWatchFeedEntrypoint: false },
                                operationName: 'fetchPlaylist',
                                extensions: { persistedQuery: { version: 1, sha256Hash: window.getGqlHash('fetchPlaylist', '346811f856fb0b7e4f6c59f8ebea78dd081c6e2fb01b77c954b26259d5fc6763') } }
                            })
                        });
                        if (!resp.ok) throw new Error('Failed to fetch playlist: ' + resp.statusText);
                        var data = await resp.json();
                        var playlistObj = data.data && (data.data.playlistV2 || data.data.playlist || data.data.playlistUnion || data.data.me && data.data.me.library || data.data.me);
                        var content = playlistObj && (playlistObj.content || playlistObj.tracks) || playlistObj;
                        var items = (content && content.items) || (playlistObj && playlistObj.items) || [];
                        var tracks = items.map(function(elem) {
                            var itemV2 = elem.itemV2 || elem.item;
                            var trackData = (itemV2 && itemV2.data) || elem.track || itemV2 || elem;
                            if (!trackData) return null;
                            var uri = trackData.uri || trackData._uri || elem.uri || (trackData.id ? 'spotify:track:' + trackData.id : '') || '';
                            if (!uri || uri.indexOf(':track:') === -1) return null;
                            var artists = (trackData.artists && trackData.artists.items) ? trackData.artists.items.map(function(a) { return a.profile && a.profile.name; }).filter(Boolean) : (trackData.artists || []).map(function(a) { return a.name; }).filter(Boolean);
                            var albumData = trackData.albumOfTrack || trackData.album || {};
                            var coverUrl = (albumData.coverArt && albumData.coverArt.sources && albumData.coverArt.sources[0] && albumData.coverArt.sources[0].url) || (albumData.images && albumData.images[0] && albumData.images[0].url) || null;
                            return { id: uri, name: trackData.name || trackData.title || 'Unknown Track', image: coverUrl, artists: artists, browsable: false };
                        }).filter(Boolean);
                        AndBridge.onMediaItemsLoaded(parentId, JSON.stringify(tracks));
                        return;
                    }

                    if (parentId.indexOf('spotify:album:') === 0) {
                        var albumId = parentId.split(':')[2];
                        var resp = await (window.mngFetch || oriFetch)('https://api-partner.spotify.com/pathfinder/v2/query', {
                            method: 'POST',
                            headers: {
                                'Authorization': window.spotAuthToken,
                                'Client-Token': window.spotCliToken,
                                'Content-Type': 'application/json;charset=UTF-8',
                                'app-platform': 'WebPlayer',
                                'spotify-app-version': '1.2.40.0'
                            },
                            body: JSON.stringify({
                                variables: { uri: 'spotify:album:' + albumId, locale: '', offset: 0, limit: 100 },
                                operationName: 'getAlbum',
                                extensions: { persistedQuery: { version: 1, sha256Hash: window.getGqlHash('getAlbum', 'b9bfabef66ed756e5e13f68a942deb60bd4125ec1f1be8cc42769dc0259b4b10') } }
                            })
                        });
                        if (!resp.ok) throw new Error('Failed to fetch album: ' + resp.statusText);
                        var data = await resp.json();
                        var albumData = data.data && (data.data.albumUnion || data.data.album || data.data.albumV2);
                        var albumCover = (albumData && albumData.coverArt && albumData.coverArt.sources && albumData.coverArt.sources[0] && albumData.coverArt.sources[0].url) || (albumData && albumData.images && albumData.images[0] && albumData.images[0].url) || null;
                        var items = (albumData && (albumData.tracksV2 && albumData.tracksV2.items || albumData.tracks && albumData.tracks.items || albumData.tracks)) || [];
                        var tracks = items.map(function(elem) {
                            var trackObj = elem.track || (elem.item && elem.item.data) || elem;
                            if (!trackObj) return null;
                            var uri = trackObj.uri || trackObj._uri || (trackObj.id ? 'spotify:track:' + trackObj.id : '') || '';
                            if (!uri) return null;
                            var artists = (trackObj.artists && trackObj.artists.items) ? trackObj.artists.items.map(function(a) { return a.profile && a.profile.name; }).filter(Boolean) : [];
                            return { id: uri, name: trackObj.name || trackObj.title || 'Unknown Track', image: albumCover, artists: artists, browsable: false };
                        }).filter(Boolean);
                        AndBridge.onMediaItemsLoaded(parentId, JSON.stringify(tracks));
                        return;
                    }

                    if (parentId.indexOf('spotify:artist:') === 0) {
                        var artistId = parentId.split(':')[2];
                        var resp = await (window.mngFetch || oriFetch)('https://api-partner.spotify.com/pathfinder/v2/query', {
                            method: 'POST',
                            headers: {
                                'Authorization': window.spotAuthToken,
                                'Client-Token': window.spotCliToken,
                                'Content-Type': 'application/json;charset=UTF-8',
                                'app-platform': 'WebPlayer',
                                'spotify-app-version': '1.2.40.0'
                            },
                            body: JSON.stringify({
                                variables: { uri: 'spotify:artist:' + artistId, locale: '' },
                                operationName: 'queryArtistOverview',
                                extensions: { persistedQuery: { version: 1, sha256Hash: window.getGqlHash('queryArtistOverview', '5b9e64f43843fa3a9b6a98543600299b0a2cbbbccfdcdcef2402eb9c1017ca4c') } }
                            })
                        });
                        if (!resp.ok) throw new Error('Failed to fetch artist: ' + resp.statusText);
                        var data = await resp.json();
                        var artistData = data.data && (data.data.artistUnion || data.data.artist);
                        var items = (artistData && (artistData.discography && artistData.discography.topTracks && artistData.discography.topTracks.items || artistData.topTracks && artistData.topTracks.items || artistData.topTracks)) || [];
                        var tracks = items.map(function(elem) {
                            var trackObj = elem.track || (elem.item && elem.item.data) || elem;
                            if (!trackObj) return null;
                            var uri = trackObj.uri || trackObj._uri || (trackObj.id ? 'spotify:track:' + trackObj.id : '') || '';
                            if (!uri) return null;
                            var artists = (trackObj.artists && trackObj.artists.items) ? trackObj.artists.items.map(function(a) { return a.profile && a.profile.name; }).filter(Boolean) : [];
                            var albumData = trackObj.albumOfTrack || trackObj.album || {};
                            var coverUrl = (albumData.coverArt && albumData.coverArt.sources && albumData.coverArt.sources[0] && albumData.coverArt.sources[0].url) || (albumData.images && albumData.images[0] && albumData.images[0].url) || null;
                            return { id: uri, name: trackObj.name || trackObj.title || 'Unknown Track', image: coverUrl, artists: artists, browsable: false };
                        }).filter(Boolean);
                        AndBridge.onMediaItemsLoaded(parentId, JSON.stringify(tracks));
                        return;
                    }

                    if (parentId.indexOf('spotify:show:') === 0) {
                        var showId = parentId.split(':')[2];
                        var resp = await (window.mngFetch || oriFetch)('https://api.spotify.com/v1/shows/' + showId + '/episodes?limit=50', {
                            headers: {
                                'Authorization': window.spotAuthToken,
                                'Client-Token': window.spotCliToken,
                                'app-platform': 'WebPlayer'
                            }
                        });
                        if (!resp.ok) throw new Error('Failed to fetch episodes: ' + resp.statusText);
                        var data = await resp.json();
                        var episodes = (data.items || []).map(function(e) {
                            var uri = e.uri || e._uri || (e.id ? 'spotify:episode:' + e.id : '') || '';
                            return { id: uri, name: e.name, image: (e.images && e.images[0] && e.images[0].url) || null, browsable: false };
                        });
                        AndBridge.onMediaItemsLoaded(parentId, JSON.stringify(episodes));
                        return;
                    }

                    AndBridge.onMediaItemsLoaded(parentId, '[]');
                } catch (e) {
                    console.error('Error loading media items for ' + parentId, e);
                    AndBridge.onMediaItemsLoaded(parentId, '[]');
                }
            }
            window.fetchMediaItems = fetchMediaItems;

            async function searchMediaItems(query) {
                try {
                    var resp = await (window.mngFetch || oriFetch)('https://api-partner.spotify.com/pathfinder/v2/query', {
                        method: 'POST',
                        headers: {
                            'Authorization': window.spotAuthToken,
                            'Client-Token': window.spotCliToken,
                            'Content-Type': 'application/json;charset=UTF-8',
                            'app-platform': 'WebPlayer',
                            'spotify-app-version': '1.2.40.0'
                        },
                        body: JSON.stringify({
                            variables: { searchTerm: query, offset: 0, limit: 30, numberOfTopResults: 5, includeAudiobooks: false, includeArtistHasConcertsField: false, includePreReleases: false, includeLocalConcertsField: false, includeAuthors: false },
                            operationName: 'searchDesktop',
                            extensions: { persistedQuery: { version: 1, sha256Hash: window.getGqlHash('searchDesktop', '4801118d4a100f756e833d33984436a3899cff359c532f8fd3aaf174b60b3b49') } }
                        })
                    });
                    if (!resp.ok) throw new Error('Search failed: ' + resp.statusText);
                    var data = await resp.json();
                    var searchData = data.data && (data.data.searchV2 || data.data.search);
                    var results = [];
                    var seenUris = new Set();

                    var pushItem = function(item) {
                        if (!item || !item.id || seenUris.has(item.id)) return;
                        seenUris.add(item.id);
                        results.push(item);
                    };

                    var topResults = (searchData && (searchData.topResultsV2 && searchData.topResultsV2.items || searchData.topResults && searchData.topResults.items)) || [];
                    topResults.forEach(function(elem) {
                        var itemWrapper = elem.item || elem;
                        var data = itemWrapper.data || itemWrapper;
                        if (!data) return;
                        var typeName = data.__typename || '';
                        var uri = itemWrapper.uri || data.uri || (data.id ? 'spotify:' + typeName.toLowerCase().replace('responsewrapper', '') + ':' + data.id : '') || '';
                        if (!uri) return;
                        var type = 'Song';
                        var isBrowsable = false;
                        var artistsList = [];
                        var img = null;
                        if (uri.indexOf(':track:') !== -1) {
                            type = 'Song'; isBrowsable = false;
                            artistsList = (data.artists && data.artists.items) ? data.artists.items.map(function(a) { return a.profile && a.profile.name; }).filter(Boolean) : (data.artists || []).map(function(a) { return a.name; }).filter(Boolean);
                            var albumData = data.albumOfTrack || data.album || {};
                            img = (albumData.coverArt && albumData.coverArt.sources && albumData.coverArt.sources[0] && albumData.coverArt.sources[0].url) || (albumData.images && albumData.images[0] && albumData.images[0].url) || null;
                        } else if (uri.indexOf(':artist:') !== -1) {
                            type = 'Artist'; isBrowsable = true;
                            img = (data.visuals && data.visuals.avatarImage && data.visuals.avatarImage.sources && data.visuals.avatarImage.sources[0] && data.visuals.avatarImage.sources[0].url) || (data.images && data.images[0] && data.images[0].url) || null;
                        } else if (uri.indexOf(':album:') !== -1) {
                            type = 'Album'; isBrowsable = true;
                            artistsList = (data.artists && data.artists.items) ? data.artists.items.map(function(a) { return a.profile && a.profile.name; }).filter(Boolean) : [];
                            img = (data.coverArt && data.coverArt.sources && data.coverArt.sources[0] && data.coverArt.sources[0].url) || (data.images && data.images[0] && data.images[0].url) || null;
                        } else if (uri.indexOf(':playlist:') !== -1) {
                            type = 'Playlist'; isBrowsable = true;
                            var owner = (data.ownerV2 && data.ownerV2.data && data.ownerV2.data.name) || (data.owner && data.owner.name) || '';
                            if (owner) artistsList = [owner];
                            img = (data.images && data.images.items && data.images.items[0] && data.images.items[0].sources && data.images.items[0].sources[0] && data.images.items[0].sources[0].url) || (data.images && data.images[0] && data.images[0].url) || null;
                        }
                        pushItem({ id: uri, name: data.name || data.title || 'Top Result', image: img, type: type, artists: artistsList, browsable: isBrowsable });
                    });

                    var rawSongs = [];
                    (searchData && (searchData.tracksV2 && searchData.tracksV2.items || searchData.tracks && searchData.tracks.items) || []).forEach(function(elem) {
                        var itemWrapper = elem.item || elem;
                        var data = itemWrapper.data || itemWrapper;
                        if (!data) return;
                        var uri = itemWrapper.uri || data.uri || (data.id ? 'spotify:track:' + data.id : '') || '';
                        if (!uri || uri.indexOf(':track:') === -1) return;
                        var artistsList = (data.artists && data.artists.items) ? data.artists.items.map(function(a) { return a.profile && a.profile.name; }).filter(Boolean) : (data.artists || []).map(function(a) { return a.name; }).filter(Boolean);
                        var albumData = data.albumOfTrack || data.album || {};
                        var coverUrl = (albumData.coverArt && albumData.coverArt.sources && albumData.coverArt.sources[0] && albumData.coverArt.sources[0].url) || (albumData.images && albumData.images[0] && albumData.images[0].url) || null;
                        rawSongs.push({ id: uri, name: data.name || data.title || 'Track', image: coverUrl, type: 'Song', artists: artistsList, browsable: false });
                    });

                    var rawAlbums = [];
                    (searchData && (searchData.albumsV2 && searchData.albumsV2.items || searchData.albums && searchData.albums.items) || []).forEach(function(elem) {
                        var item = elem.data || elem;
                        if (!item) return;
                        var uri = item.uri || (item.id ? 'spotify:album:' + item.id : '');
                        if (!uri) return;
                        var name = item.name || 'Album';
                        var img = (item.coverArt && item.coverArt.sources && item.coverArt.sources[0] && item.coverArt.sources[0].url) || (item.images && item.images[0] && item.images[0].url) || null;
                        var artists = (item.artists && item.artists.items) ? item.artists.items.map(function(a) { return a.profile && a.profile.name; }).filter(Boolean) : [];
                        rawAlbums.push({ id: uri, name: name, image: img, type: 'Album', artists: artists, browsable: true });
                    });

                    var rawPlaylists = [];
                    (searchData && searchData.playlists && searchData.playlists.items || []).forEach(function(elem) {
                        var item = elem.data || elem;
                        if (!item) return;
                        var uri = item.uri || (item.id ? 'spotify:playlist:' + item.id : '');
                        if (!uri) return;
                        var name = item.name || item.title || 'Playlist';
                        var img = (item.images && item.images.items && item.images.items[0] && item.images.items[0].sources && item.images.items[0].sources[0] && item.images.items[0].sources[0].url) || (item.images && item.images[0] && item.images[0].url) || null;
                        var owner = (item.ownerV2 && item.ownerV2.data && item.ownerV2.data.name) || (item.owner && item.owner.name) || '';
                        rawPlaylists.push({ id: uri, name: name, image: img, type: 'Playlist', artists: owner ? [owner] : [], browsable: true });
                    });

                    var rawArtists = [];
                    (searchData && searchData.artists && searchData.artists.items || []).forEach(function(elem) {
                        var item = elem.data || elem;
                        if (!item) return;
                        var uri = item.uri || (item.id ? 'spotify:artist:' + item.id : '');
                        if (!uri) return;
                        var name = (item.profile && item.profile.name) || item.name || 'Artist';
                        var img = (item.visuals && item.visuals.avatarImage && item.visuals.avatarImage.sources && item.visuals.avatarImage.sources[0] && item.visuals.avatarImage.sources[0].url) || (item.images && item.images[0] && item.images[0].url) || null;
                        rawArtists.push({ id: uri, name: name, image: img, type: 'Artist', artists: [], browsable: true });
                    });

                    rawSongs.slice(0, 4).forEach(pushItem);
                    rawAlbums.slice(0, 3).forEach(pushItem);
                    rawPlaylists.slice(0, 3).forEach(pushItem);
                    rawArtists.slice(0, 2).forEach(pushItem);
                    rawSongs.slice(4).forEach(pushItem);
                    rawAlbums.slice(3).forEach(pushItem);
                    rawPlaylists.slice(3).forEach(pushItem);
                    rawArtists.slice(2).forEach(pushItem);

                    AndBridge.onSearchCompleted(query, JSON.stringify(results));
                } catch (e) {
                    console.error('Search error for ' + query, e);
                    AndBridge.onSearchCompleted(query, '[]');
                }
            }
            window.searchMediaItems = searchMediaItems;
        
    """
}