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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Pair;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;

import org.gateshipone.malp.application.artworkdatabase.network.MALPRequestQueue;
import org.gateshipone.malp.application.artworkdatabase.network.artprovider.FanartTVManager;
import org.gateshipone.malp.application.artworkdatabase.network.artprovider.LastFMManager;
import org.gateshipone.malp.application.artworkdatabase.network.artprovider.MusicBrainzManager;
import org.gateshipone.malp.application.artworkdatabase.network.responses.AlbumFetchError;
import org.gateshipone.malp.application.artworkdatabase.network.responses.AlbumImageResponse;
import org.gateshipone.malp.application.artworkdatabase.network.responses.ArtistFetchError;
import org.gateshipone.malp.application.artworkdatabase.network.responses.ArtistImageResponse;
import org.gateshipone.malp.mpdservice.handlers.responsehandler.MPDResponseAlbumList;
import org.gateshipone.malp.mpdservice.handlers.responsehandler.MPDResponseArtistList;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDArtist;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFile;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class ArtworkManager implements ArtistFetchError, AlbumFetchError {
    private static final String TAG = ArtworkManager.class.getSimpleName();

    private ArtworkDatabaseManager mDBManager;
    private final ArrayList<onNewArtistImageListener> mArtistListeners;

    private final ArrayList<onNewAlbumImageListener> mAlbumListeners;

    private static ArtworkManager mInstance;
    private Context mContext;

    private final List<MPDAlbum> mAlbumList = new ArrayList<>();
    private final List<MPDArtist> mArtistList = new ArrayList<>();

    private MPDAlbum mCurrentBulkAlbum = null;
    private MPDArtist mCurrentBulkArtist = null;

    private BulkLoadingProgressCallback mBulkProgressCallback;

    private ArtworkManager(Context context) {

        mDBManager = ArtworkDatabaseManager.getInstance(context);

        mArtistListeners = new ArrayList<>();
        mAlbumListeners = new ArrayList<>();


        mContext = context;

        ConnectionStateReceiver receiver = new ConnectionStateReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mContext.registerReceiver(receiver, filter);
    }

    public static synchronized ArtworkManager getInstance(Context context) {
        if (null == mInstance) {
            mInstance = new ArtworkManager(context);
        }
        return mInstance;
    }

    public Bitmap getArtistImage(final MPDArtist artist) throws ImageNotFoundException {
        if (null == artist) {
            return null;
        }


        byte[] image;

        /**
         * If no artist id is set for the album (possible with data set of Odyssey) check
         * the artist with name instead of id.
         */
        if (artist.getMBIDCount() != 0) {
            image = mDBManager.getArtistImage(artist);
        } else {
            image = mDBManager.getArtistImage(artist.getArtistName());
        }


        // Checks if the database has an image for the requested artist
        if (null != image) {
            // Create a bitmap from the data blob in the database
            return BitmapFactory.decodeByteArray(image, 0, image.length);
        }
        return null;
    }

    public Bitmap getAlbumImageFromMBID(final String mbid) throws ImageNotFoundException {
        if (null == mbid) {
            return null;
        }


        byte[] image;


        image = mDBManager.getAlbumImageFromMBID(mbid);

        // Checks if the database has an image for the requested album
        if (null != image) {
            // Create a bitmap from the data blob in the database
            return BitmapFactory.decodeByteArray(image, 0, image.length);

        }
        return null;
    }

    public Bitmap getAlbumImageFromAlbumNameArtistName(final String albumName, final String artistName) throws ImageNotFoundException {
        if (null == albumName || null == artistName) {
            return null;
        }


        byte[] image;


        image = mDBManager.getAlbumImage(albumName, artistName);

        // Checks if the database has an image for the requested album
        if (null != image) {
            // Create a bitmap from the data blob in the database
            return BitmapFactory.decodeByteArray(image, 0, image.length);

        }
        return null;
    }

    public Bitmap getAlbumImageFromName(final String albumName) throws ImageNotFoundException {
        if (null == albumName) {
            return null;
        }


        byte[] image;


        image = mDBManager.getAlbumImage(albumName);

        // Checks if the database has an image for the requested album
        if (null != image) {
            // Create a bitmap from the data blob in the database
            return BitmapFactory.decodeByteArray(image, 0, image.length);

        }
        return null;
    }

    public Bitmap getAlbumImageForTrack(final MPDFile track) throws ImageNotFoundException {
        if (null == track) {
            return null;
        }
        Bitmap image = null;
        if (!track.getTrackAlbumMBID().isEmpty()) {
            try {
                image = getAlbumImageFromMBID(track.getTrackAlbumMBID());
            } catch (ImageNotFoundException e) {
            }
            if (null != image) {
                return image;
            }
        }

        // Try to get image from Albumname/Album artistname
        try {
            image = getAlbumImageFromAlbumNameArtistName(track.getTrackAlbum(), track.getTrackAlbumArtist());
        } catch (ImageNotFoundException e) {
        }
        if (null != image) {
            return image;
        }

        try {
            image = getAlbumImageFromAlbumNameArtistName(track.getTrackAlbum(), track.getTrackArtist());
        } catch (ImageNotFoundException e) {
        }
        if (null != image) {
            return image;
        }

        // Last resort, try just the name

        image = getAlbumImageFromName(track.getTrackAlbum());

        if (null != image) {
            return image;
        } else {
            return null;
        }

    }

    public Bitmap getAlbumImage(final MPDAlbum album) throws ImageNotFoundException {
        if (null == album) {
            return null;
        }


        byte[] image;

        if (album.getMBID().isEmpty()) {
            // Check if ID is available (should be the case). If not use the album name for
            // lookup.
            // FIXME use artistname also
            image = mDBManager.getAlbumImage(album.getName());
        } else {
            // If id is available use it.
            image = mDBManager.getAlbumImage(album);
        }

        // Checks if the database has an image for the requested album
        if (null != image) {
            // Create a bitmap from the data blob in the database
            return BitmapFactory.decodeByteArray(image, 0, image.length);

        }
        return null;
    }

    /**
     * Starts an asynchronous fetch for the image of the given artist.
     *
     * @param artist Artist to fetch an image for.
     */
    public void fetchArtistImage(final MPDArtist artist) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
        String artistProvider = sharedPref.getString("pref_artist_provider", "last_fm");

        ConnectivityManager cm =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        boolean wifiOnly = sharedPref.getBoolean("pref_download_wifi_only", true);

        boolean isWifi = cm.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_WIFI || cm.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_ETHERNET;

        if (wifiOnly && !isWifi) {
            return;
        }

        if (artistProvider.equals("last_fm")) {
            LastFMManager.getInstance(mContext).fetchArtistImage(artist, new Response.Listener<ArtistImageResponse>() {
                @Override
                public void onResponse(ArtistImageResponse response) {
                    new InsertArtistImageTask().execute(response);
                }
            }, this);
        } else if (artistProvider.equals("fanart_tv")) {
            FanartTVManager.getInstance(mContext).fetchArtistImage(artist, new Response.Listener<ArtistImageResponse>() {
                @Override
                public void onResponse(ArtistImageResponse response) {
                    new InsertArtistImageTask().execute(response);
                }
            }, this);
        }
    }

    /**
     * Starts an asynchronous fetch for the image of the given album
     *
     * @param album Album to fetch an image for.
     */
    public void fetchAlbumImage(final MPDAlbum album) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
        String albumProvider = sharedPref.getString("pref_album_provider", "musicbrainz");

        boolean wifiOnly = sharedPref.getBoolean("pref_download_wifi_only", true);

        ConnectivityManager cm =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        boolean isWifi = cm.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_WIFI || cm.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_ETHERNET;

        if (wifiOnly && !isWifi) {
            return;
        }

        if (albumProvider.equals("musicbrainz")) {
            MusicBrainzManager.getInstance(mContext).fetchAlbumImage(album, new Response.Listener<AlbumImageResponse>() {
                @Override
                public void onResponse(AlbumImageResponse response) {
                    new InsertAlbumImageTask().execute(response);
                }
            }, this);
        } else if (albumProvider.equals("last_fm")) {
            LastFMManager.getInstance(mContext).fetchAlbumImage(album, new Response.Listener<AlbumImageResponse>() {
                @Override
                public void onResponse(AlbumImageResponse response) {
                    new InsertAlbumImageTask().execute(response);
                }
            }, this);
        }
    }

    /**
     * Starts an asynchronous fetch for the image of the given album
     *
     * @param track Track to be used for image fetching
     */
    public void fetchAlbumImage(final MPDFile track) {
        // Create a dummy album
        MPDAlbum album = new MPDAlbum(track.getTrackAlbum());
        album.setMBID(track.getTrackAlbumMBID());
        album.setArtistName(track.getTrackAlbumArtist());

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
        String albumProvider = sharedPref.getString("pref_album_provider", "musicbrainz");

        boolean wifiOnly = sharedPref.getBoolean("pref_download_wifi_only", true);

        ConnectivityManager cm =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        boolean isWifi = cm.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_WIFI || cm.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_ETHERNET;

        if (wifiOnly && !isWifi) {
            return;
        }

        if (albumProvider.equals("musicbrainz")) {
            MusicBrainzManager.getInstance(mContext).fetchAlbumImage(album, new Response.Listener<AlbumImageResponse>() {
                @Override
                public void onResponse(AlbumImageResponse response) {
                    new InsertAlbumImageTask().execute(response);
                }
            }, this);
        } else if (albumProvider.equals("last_fm")) {
            LastFMManager.getInstance(mContext).fetchAlbumImage(album, new Response.Listener<AlbumImageResponse>() {
                @Override
                public void onResponse(AlbumImageResponse response) {
                    new InsertAlbumImageTask().execute(response);
                }
            }, this);
        }
    }

    /**
     * Registers a listener that gets notified when a new artist image was added to the dataset.
     *
     * @param listener Listener to register
     */
    public void registerOnNewArtistImageListener(onNewArtistImageListener listener) {
        if (null != listener) {
            synchronized (mArtistListeners) {
                mArtistListeners.add(listener);
            }
        }
    }

    /**
     * Unregisters a listener that got notified when a new artist image was added to the dataset.
     *
     * @param listener Listener to unregister
     */
    public void unregisterOnNewArtistImageListener(onNewArtistImageListener listener) {
        if (null != listener) {
            synchronized (mArtistListeners) {
                mArtistListeners.remove(listener);
            }
        }
    }

    /**
     * Registers a listener that gets notified when a new album image was added to the dataset.
     *
     * @param listener Listener to register
     */
    public void registerOnNewAlbumImageListener(onNewAlbumImageListener listener) {
        if (null != listener) {
            synchronized (mArtistListeners) {
                mAlbumListeners.add(listener);
            }
        }
    }

    /**
     * Unregisters a listener that got notified when a new album image was added to the dataset.
     *
     * @param listener Listener to unregister
     */
    public void unregisterOnNewAlbumImageListener(onNewAlbumImageListener listener) {
        if (null != listener) {
            synchronized (mArtistListeners) {
                mAlbumListeners.remove(listener);
            }
        }
    }

    /**
     * Interface implementation to handle errors during fetching of artist images
     *
     * @param artist Artist that resulted in a fetch error
     */
    @Override
    public void fetchError(MPDArtist artist) {
        Log.e(TAG, "Error fetching artist: " + artist.getArtistName());
        ArtistImageResponse imageResponse = new ArtistImageResponse();
        imageResponse.artist = artist;
        imageResponse.image = null;
        imageResponse.url = null;
        new InsertArtistImageTask().execute(imageResponse);
    }

    /**
     * Interface implementation to handle errors during fetching of album images
     *
     * @param album Album that resulted in a fetch error
     */
    @Override
    public void fetchError(MPDAlbum album) {
        Log.e(TAG, "Error fetching album: " + album.getName() + "-" + album.getArtistName());
        AlbumImageResponse imageResponse = new AlbumImageResponse();
        imageResponse.album = album;
        imageResponse.image = null;
        imageResponse.url = null;
        new InsertAlbumImageTask().execute(imageResponse);
    }

    /**
     * AsyncTask to insert the images to the SQLdatabase. This is necessary as the Volley response
     * is handled in the UI thread.
     */
    private class InsertArtistImageTask extends AsyncTask<ArtistImageResponse, Object, MPDArtist> {

        /**
         * Inserts the image to the database.
         *
         * @param params Pair of byte[] (containing the image itself) and MPDArtist for which the image is for
         * @return the artist model that was inserted to the database.
         */
        @Override
        protected MPDArtist doInBackground(ArtistImageResponse... params) {
            ArtistImageResponse response = params[0];
            if (mCurrentBulkArtist == response.artist) {
                fetchNextBulkArtist();
            }


            if (response.image == null) {
                mDBManager.insertArtistImage(response.artist, response.image);
                return response.artist;
            }

            // Rescale them if to big
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(response.image, 0, response.image.length, options);
            if ((options.outHeight > 500 || options.outWidth > 500)) {
                Log.v(TAG, "Image to big, rescaling");
                options.inJustDecodeBounds = false;
                Bitmap bm = BitmapFactory.decodeByteArray(response.image, 0, response.image.length, options);
                ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                bm.createScaledBitmap(bm, 500, 500, true).compress(Bitmap.CompressFormat.JPEG, 80, byteStream);
                mDBManager.insertArtistImage(response.artist, byteStream.toByteArray());
            } else {
                mDBManager.insertArtistImage(response.artist, response.image);
            }

            return response.artist;
        }

        /**
         * Notifies the listeners about a change in the image dataset. Called in the UI thread.
         *
         * @param result Artist that was inserted in the database
         */
        protected void onPostExecute(MPDArtist result) {
            synchronized (mArtistListeners) {
                for (onNewArtistImageListener artistListener : mArtistListeners) {
                    artistListener.newArtistImage(result);
                }
            }
        }

    }

    /**
     * AsyncTask to insert the images to the SQLdatabase. This is necessary as the Volley response
     * is handled in the UI thread.
     */
    private class InsertAlbumImageTask extends AsyncTask<AlbumImageResponse, Object, MPDAlbum> {

        /**
         * Inserts the image to the database.
         *
         * @param params Pair of byte[] (containing the image itself) and MPDAlbum for which the image is for
         * @return the album model that was inserted to the database.
         */
        @Override
        protected MPDAlbum doInBackground(AlbumImageResponse... params) {
            AlbumImageResponse response = params[0];
            if (mCurrentBulkAlbum == response.album) {
                fetchNextBulkAlbum();
            }
            if (response.image == null) {
                mDBManager.insertAlbumImage(response.album, response.image);
                return response.album;
            }

            Log.v(TAG, "Inserting image for album: " + response.album.getName());
            // Rescale them if to big
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(response.image, 0, response.image.length, options);
            if ((options.outHeight > 500 || options.outWidth > 500)) {
                Log.v(TAG, "Image to big, rescaling");
                options.inJustDecodeBounds = false;
                Bitmap bm = BitmapFactory.decodeByteArray(response.image, 0, response.image.length, options);
                ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                bm.createScaledBitmap(bm, 500, 500, true).compress(Bitmap.CompressFormat.JPEG, 80, byteStream);
                mDBManager.insertAlbumImage(response.album, byteStream.toByteArray());
            } else {
                mDBManager.insertAlbumImage(response.album, response.image);
            }

            return response.album;
        }

        /**
         * Notifies the listeners about a change in the image dataset. Called in the UI thread.
         *
         * @param result Album that was inserted in the database
         */
        protected void onPostExecute(MPDAlbum result) {
            synchronized (mAlbumListeners) {
                for (onNewAlbumImageListener albumListener : mAlbumListeners) {
                    albumListener.newAlbumImage(result);
                }
            }
        }

    }

    private class ParseMPDAlbumListTask extends AsyncTask<List<MPDAlbum>, Object, Object> {

        @Override
        protected Object doInBackground(List<MPDAlbum>... lists) {
            List<MPDAlbum> albumList = lists[0];

            mBulkProgressCallback.startAlbumLoading(albumList.size());

            Log.v(TAG, "Received " + albumList.size() + " albums for bulk loading");
            synchronized (mAlbumList) {
                mAlbumList.clear();
                mAlbumList.addAll(albumList);
            }
            if ( !mArtistList.isEmpty() ) {
                fetchNextBulkAlbum();
                fetchNextBulkArtist();
            }
            return null;
        }
    }

    private class ParseMPDArtistListTask extends AsyncTask<List<MPDArtist>, Object, Object> {

        @Override
        protected Object doInBackground(List<MPDArtist>... lists) {
            List<MPDArtist> artistList = lists[0];

            Log.v(TAG, "Received " + artistList.size() + " artists for bulk loading");
            mBulkProgressCallback.startArtistLoading(artistList.size());
            synchronized (mArtistList) {
                mArtistList.clear();
                mArtistList.addAll(artistList);
            }
            if ( !mAlbumList.isEmpty() ) {
                fetchNextBulkAlbum();
                fetchNextBulkArtist();
            }
            return null;
        }
    }

    public void bulkLoadImages(BulkLoadingProgressCallback progressCallback) {
        if (progressCallback == null) {
            return;
        }
        mBulkProgressCallback = progressCallback;
        Log.v(TAG, "Start bulk loading");
        MPDQueryHandler.getAlbums(new MPDResponseAlbumList() {
            @Override
            public void handleAlbums(List<MPDAlbum> albumList) {
                new ParseMPDAlbumListTask().execute(albumList);
            }
        });
        MPDQueryHandler.getArtists(new MPDResponseArtistList() {
            @Override
            public void handleArtists(List<MPDArtist> artistList) {
                new ParseMPDArtistListTask().execute(artistList);
            }
        });
    }

    private void fetchNextBulkAlbum() {
        boolean isEmpty;
        synchronized (mAlbumList) {
            isEmpty = mAlbumList.isEmpty();
        }

        while (!isEmpty) {
            MPDAlbum album;
            synchronized (mAlbumList) {
                album = mAlbumList.remove(0);
                Log.v(TAG, "Bulk load next album: " + album.getName() + ":" + album.getArtistName() + " remaining: " + mAlbumList.size());
                mBulkProgressCallback.albumsRemaining(mAlbumList.size());
            }
            mCurrentBulkAlbum = album;

            // Check if image already there
            try {
                mDBManager.getAlbumImage(album);
                // If this does not throw the exception it already has an image.
            } catch (ImageNotFoundException e) {
                fetchAlbumImage(album);
                return;
            }

            synchronized (mAlbumList) {
                isEmpty = mAlbumList.isEmpty();
            }
        }
        if ( mArtistList.isEmpty() ) {
            mBulkProgressCallback.finishedLoading();
        }

    }

    private void fetchNextBulkArtist() {
        boolean isEmpty;
        synchronized (mArtistList) {
            isEmpty = mArtistList.isEmpty();
        }

        while (!isEmpty) {
            MPDArtist artist;
            synchronized (mArtistList) {
                artist = mArtistList.remove(0);
                Log.v(TAG, "Bulk load next artist: " + artist.getArtistName() + " remaining: " + mArtistList.size());
                mBulkProgressCallback.artistsRemaining(mArtistList.size());
            }
            mCurrentBulkArtist = artist;

            // Check if image already there
            try {
                mDBManager.getArtistImage(artist);
                // If this does not throw the exception it already has an image.
            } catch (ImageNotFoundException e) {
                fetchArtistImage(artist);
                return;
            }

            synchronized (mArtistList) {
                isEmpty = mArtistList.isEmpty();
            }
        }

        if ( mAlbumList.isEmpty() ) {
            mBulkProgressCallback.finishedLoading();
        }

    }

    /**
     * Interface used for adapters to be notified about data set changes
     */
    public interface onNewArtistImageListener {
        void newArtistImage(MPDArtist artist);
    }

    /**
     * Interface used for adapters to be notified about data set changes
     */
    public interface onNewAlbumImageListener {
        void newAlbumImage(MPDAlbum album);
    }

    private class ConnectionStateReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);

            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            if (null == netInfo) {
                return;
            }
            boolean wifiOnly = sharedPref.getBoolean("pref_download_wifi_only", true);
            boolean isWifi = netInfo.getType() == ConnectivityManager.TYPE_WIFI || netInfo.getType() == ConnectivityManager.TYPE_ETHERNET;

            if (wifiOnly && !isWifi) {
                // Cancel all downloads
                Log.v(TAG, "Cancel all downloads because of connection change");
                cancelAllRequests();
            }

        }
    }

    /**
     * This will cancel the last used album/artist image providers. To make this useful on connection change
     * it is important to cancel all requests when changing the provider in settings.
     */
    public void cancelAllRequests() {
        Log.v(TAG,"Cancel all download requests");
        MALPRequestQueue.getInstance(mContext).cancelAll(new RequestQueue.RequestFilter() {
            @Override
            public boolean apply(Request<?> request) {
                return true;
            }
        });

        // Stop bulk loading as well
        synchronized (mAlbumList) {
            mAlbumList.clear();
        }
        synchronized (mArtistList) {
            mArtistList.clear();
        }

        if ( null != mBulkProgressCallback ) {
            mBulkProgressCallback.finishedLoading();
        }
    }

    public interface BulkLoadingProgressCallback {
        void startAlbumLoading(int albumCount);

        void startArtistLoading(int artistCount);

        void albumsRemaining(int remainingAlbums);

        void artistsRemaining(int remainingArtists);

        void finishedLoading();
    }
}
