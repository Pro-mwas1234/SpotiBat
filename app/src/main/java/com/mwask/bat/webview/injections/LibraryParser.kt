package com.project.lol.webview.injections

object LibraryParser {
    const val CONTENT = """
            window.parseLibrary = function(items) {
                var res={playlists:[],albums:[],artists:[],podcasts:[]};
                items.forEach(function(entry){
                    var data = (entry.item && entry.item.data) || entry.item || entry;
                    if(!data || !data.__typename) return;
                    var typename = data.__typename;
                    var uri = data.uri || data._uri || '';
                    var name = data.name || data.title || (data.profile && data.profile.name) || 'Unknown';
                    var image = (data.images && data.images.items && data.images.items[0] && data.images.items[0].sources && data.images.items[0].sources[0] && data.images.items[0].sources[0].url)
                        || (data.images && data.images[0] && data.images[0].url)
                        || (data.coverArt && data.coverArt.sources && data.coverArt.sources[0] && data.coverArt.sources[0].url)
                        || (data.visuals && data.visuals.avatarImage && data.visuals.avatarImage.sources && data.visuals.avatarImage.sources[0] && data.visuals.avatarImage.sources[0].url)
                        || null;
                    if(!uri) return;
                    if(typename.indexOf('Playlist') !== -1 || typename.indexOf('PseudoPlaylist') !== -1 || uri.indexOf(':playlist:') !== -1) {
                        if(name === 'Liked Songs' || uri.indexOf('collection') !== -1) {
                            if(res.playlists.some(function(p){ return p.name === 'Liked Songs' || p.id.indexOf('collection') !== -1; })) return;
                            res.playlists.push({id:'spotify:collection:tracks', name:name, image:'https://misc.scdn.co/liked-songs/liked-songs-640.png'});
                        } else {
                            res.playlists.push({id:uri, name:name, image:image});
                        }
                    } else if(typename.indexOf('Album') !== -1 || uri.indexOf(':album:') !== -1) {
                        res.albums.push({id:uri, name:name, image:image, artists:data.artists&&data.artists.items?data.artists.items.map(function(a){return a.profile&&a.profile.name}).filter(Boolean):[]});
                    } else if(typename.indexOf('Artist') !== -1 || uri.indexOf(':artist:') !== -1) {
                        res.artists.push({id:uri, name:name, image:image});
                    } else if(typename.indexOf('Podcast') !== -1 || typename.indexOf('Show') !== -1 || uri.indexOf(':show:') !== -1) {
                        res.podcasts.push({id:uri, name:name, image:image, artists:data.publisher&&data.publisher.name?[data.publisher.name]:[]});
                    }
                });
                return res;
            };
        
    """
}