/*
 * Copyright (C) 2016  Hendrik Borghorst & Frederik Luetkes
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.gateshipone.malp.application.artworkdatabase;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import com.android.volley.Cache;
import com.android.volley.Network;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.NoCache;

import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDArtist;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFile;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class FanartTVManager implements ArtistImageProvider, FanartProvider {
    private static final String TAG = FanartTVManager.class.getSimpleName();

    private static final String MUSICBRAINZ_API_URL = "http://musicbrainz.org/ws/2";

    private static final String FANART_TV_API_URL = "http://webservice.fanart.tv/v3/music";

    private RequestQueue mRequestQueue;

    private static FanartTVManager mInstance;

    private static final String MUSICBRAINZ_FORMAT_JSON = "&fmt=json";

    private static final int MUSICBRAINZ_LIMIT_RESULT_COUNT = 1;
    private static final String MUSICBRAINZ_LIMIT_RESULT = "&limit=" + String.valueOf(MUSICBRAINZ_LIMIT_RESULT_COUNT);

    private static final int FANART_COUNT_LIMIT = 10;


    private static final String API_KEY = "c0cc5d1b6e807ce93e49d75e0e5d371b";

    private FanartTVManager(Context context) {
        mRequestQueue = MALPRequestQueue.getInstance(context);
    }

    public static synchronized FanartTVManager getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new FanartTVManager(context);
        }
        return mInstance;
    }


    public <T> void addToRequestQueue(Request<T> req) {
        mRequestQueue.add(req);
    }

    public void fetchArtistImage(final MPDArtist artist, final Response.Listener<Pair<byte[], MPDArtist>> listener, final ArtistFetchError errorListener) {
        if (artist.getMBIDCount() > 0) {
            Log.v(TAG, "Directly trying MPD MBID");
            tryArtistMBID(0, artist, listener, errorListener);
        } else {
            Log.v(TAG, "Manually resolving MBID");
            String artistURLName = Uri.encode(artist.getArtistName().replaceAll("/", " "));

            getArtists(artistURLName, new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    JSONArray artists = null;
                    try {
                        artists = response.getJSONArray("artists");

                        if (!artists.isNull(0)) {
                            JSONObject artistObj = artists.getJSONObject(0);
                            final String artistMBID = artistObj.getString("id");

                            getArtistImageURL(artistMBID, new Response.Listener<JSONObject>() {
                                @Override
                                public void onResponse(JSONObject response) {
                                    JSONArray thumbImages = null;
                                    try {
                                        thumbImages = response.getJSONArray("artistthumb");

                                        JSONObject firstThumbImage = thumbImages.getJSONObject(0);
                                        getArtistImage(firstThumbImage.getString("url"), artist, listener, new Response.ErrorListener() {
                                            @Override
                                            public void onErrorResponse(VolleyError error) {
                                                errorListener.fetchError(artist);
                                            }
                                        });

                                    } catch (JSONException e) {
                                        errorListener.fetchError(artist);
                                    }
                                }
                            }, new Response.ErrorListener() {
                                @Override
                                public void onErrorResponse(VolleyError error) {
                                    errorListener.fetchError(artist);
                                }
                            });
                        }
                    } catch (JSONException e) {
                        errorListener.fetchError(artist);
                    }
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    NetworkResponse networkResponse = error.networkResponse;
                    if (networkResponse != null && networkResponse.statusCode == 503) {
                        // If MusicBrainz returns 503 this is probably because of rate limiting
                        Log.e(TAG, "Rate limit reached");
                        mRequestQueue.cancelAll(new RequestQueue.RequestFilter() {
                            @Override
                            public boolean apply(Request<?> request) {
                                return true;
                            }
                        });
                    } else {
                        errorListener.fetchError(artist);
                    }
                }
            });
        }
    }

    private void tryArtistMBID(final int mbidIndex, final MPDArtist artist, final Response.Listener<Pair<byte[], MPDArtist>> listener, final ArtistFetchError errorListener) {
        if (mbidIndex < artist.getMBIDCount()) {
            getArtistImageURL(artist.getMBID(0), new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    JSONArray thumbImages = null;
                    try {
                        thumbImages = response.getJSONArray("artistthumb");

                        JSONObject firstThumbImage = thumbImages.getJSONObject(0);
                        getArtistImage(firstThumbImage.getString("url"), artist, listener, new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                // If we have multiple artist mbids try the next one
                                if (mbidIndex + 1 < artist.getMBIDCount()) {
                                    tryArtistMBID(mbidIndex + 1, artist, listener, errorListener);
                                } else {
                                    // All tried
                                    errorListener.fetchError(artist);
                                }
                            }
                        });

                    } catch (JSONException e) {
                        // If we have multiple artist mbids try the next one
                        if (mbidIndex + 1 < artist.getMBIDCount()) {
                            tryArtistMBID(mbidIndex + 1, artist, listener, errorListener);
                        } else {
                            // All tried
                            errorListener.fetchError(artist);
                        }
                    }
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    errorListener.fetchError(artist);
                }
            });
        }
    }

    private void getArtists(String artistName, Response.Listener<JSONObject> listener, Response.ErrorListener errorListener) {

        Log.v(FanartTVManager.class.getSimpleName(), artistName);

        String url = MUSICBRAINZ_API_URL + "/" + "artist/?query=artist:" + artistName + MUSICBRAINZ_LIMIT_RESULT + MUSICBRAINZ_FORMAT_JSON;

        MALPJsonObjectRequest jsonObjectRequest = new MALPJsonObjectRequest(Request.Method.GET, url, null, listener, errorListener);

        addToRequestQueue(jsonObjectRequest);
    }

    private void getArtistImageURL(String artistMBID, Response.Listener<JSONObject> listener, Response.ErrorListener errorListener) {

        Log.v(FanartTVManager.class.getSimpleName(), artistMBID);

        String url = FANART_TV_API_URL + "/" + artistMBID + "?api_key=" + API_KEY;

        MALPJsonObjectRequest jsonObjectRequest = new MALPJsonObjectRequest(Request.Method.GET, url, null, listener, errorListener);

        addToRequestQueue(jsonObjectRequest);
    }

    private void getArtistImage(String url, MPDArtist artist, Response.Listener<Pair<byte[], MPDArtist>> listener, Response.ErrorListener errorListener) {
        Log.v(FanartTVManager.class.getSimpleName(), url);

        Request<Pair<byte[], MPDArtist>> byteResponse = new ArtistImageByteRequest(url, artist, listener, errorListener);

        addToRequestQueue(byteResponse);
    }

    @Override
    public void cancelAll() {
        mRequestQueue.cancelAll(new RequestQueue.RequestFilter() {
            @Override
            public boolean apply(Request<?> request) {
                return true;
            }
        });
    }

    @Override
    public void fetchArtistFanarts(final MPDFile track, final Response.Listener<Pair<byte[], MPDArtist>> listener, final FanartFetchError errorListener) {
        // Create a dummy artist
        final MPDArtist artist;
        if (!track.getTrackAlbumArtist().isEmpty()) {
            artist = new MPDArtist(track.getTrackAlbumArtist());
        } else {
            artist = new MPDArtist(track.getTrackArtist());
        }

        if ( !track.getTrackAlbumArtistMBID().isEmpty()) {
            artist.addMBID(track.getTrackAlbumArtistMBID());
        }

        if (artist.getMBIDCount() > 0) {
            getArtistMBIDFanart(artist.getMBID(0),track,artist, listener, errorListener);
        } else {
            getArtists(artist.getArtistName(), new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    JSONArray artists = null;
                    try {
                        artists = response.getJSONArray("artists");

                        if (!artists.isNull(0)) {
                            JSONObject artistObj = artists.getJSONObject(0);
                            final String artistMBID = artistObj.getString("id");
                            getArtistMBIDFanart(artistMBID,track,artist, listener, errorListener);
                        }
                    }  catch (JSONException e) {
                        errorListener.fanartFetchError(track);
                    }
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    errorListener.fanartFetchError(track);
                }
            });
        }
    }

    private void getArtistMBIDFanart(String mbid,final MPDFile track, final MPDArtist artist, final Response.Listener<Pair<byte[], MPDArtist>> listener, final FanartFetchError errorListener ) {
        getArtistImageURL(mbid, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                try {
                    JSONArray backgroundImages = response.getJSONArray("artistbackground");
                    if (backgroundImages.length() == 0) {
                        errorListener.fanartFetchError(track);
                    } else {
                        for (int i = 0; i < backgroundImages.length() && i < FANART_COUNT_LIMIT; i++) {
                            JSONObject image = backgroundImages.getJSONObject(i);
                            getArtistImage(image.getString("url"), artist, new Response.Listener<Pair<byte[], MPDArtist>>() {
                                @Override
                                public void onResponse(Pair<byte[], MPDArtist> response) {
                                    listener.onResponse(response);
                                }
                            }, new Response.ErrorListener() {
                                @Override
                                public void onErrorResponse(VolleyError error) {
                                    error.printStackTrace();
                                }
                            });
                        }
                    }
                } catch (JSONException e) {
                    errorListener.fanartFetchError(track);
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        });
    }
}