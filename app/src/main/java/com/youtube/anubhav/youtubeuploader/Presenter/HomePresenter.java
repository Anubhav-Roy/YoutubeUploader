package com.youtube.anubhav.youtubeuploader.Presenter;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.YouTubeScopes;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;
import com.google.common.collect.Lists;
import com.youtube.anubhav.youtubeuploader.Activities.Home;
import com.youtube.anubhav.youtubeuploader.Contracts.HomeContract;
import com.youtube.anubhav.youtubeuploader.R;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class HomePresenter implements HomeContract.Presenter{

    private Context mContext;
    private HomeContract.View mView;
    private static final String[] SCOPES = {  YouTubeScopes.YOUTUBE_UPLOAD };
    private static  final String TAG = "Presenter";

    GoogleAccountCredential mCredential;

    Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            if(msg.what==-1){
                mView.updateProgressDialog(-1d);
            }else {
                mView.updateProgressDialog(msg.getData().getDouble("progress"));
            }
        }
    };

    public HomePresenter(Context mContext, HomeContract.View mView){
        this.mContext = mContext;
        this.mView = mView;
        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
                mContext.getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
    }

    /**
     * Attempt to call the API, after verifying that all the preconditions are
     * satisfied. The preconditions are: Google Play Services installed, an
     * account was selected and the device currently has online access. If any
     * of the preconditions are not satisfied, the app will prompt the user as
     * appropriate.
     */
    public void getResultsFromApi() {
        if (! isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            mView.chooseAccount();
        } else if (! isDeviceOnline()) {

        }
        else{
            mView.initVideoPicker();
        }
    }

    @Override
    public void setSelectedAccountName(String accountName) {
        mCredential.setSelectedAccountName(accountName);
    }

    /**
     * Checks whether the device currently has a network connection.
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date.
     * @return true if Google Play Services is available and up to
     *     date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(mContext);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }


    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(mContext);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            mView.showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }


    @Override
    public void startUpload(Uri uri) {
        new UploadVideo(mCredential).execute(uri);
        mView.showProgressDialog();
    }

    private class UploadVideo extends AsyncTask<Uri,Void,Void>{

        private com.google.api.services.youtube.YouTube mService = null;

        UploadVideo(GoogleAccountCredential credential) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
//        JsonFactory jsonFactory = new AndroidJsonFactory(); // GsonFactory
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

            HttpRequestInitializer initializer = new HttpRequestInitializer() {
                @Override
                public void initialize(HttpRequest request) throws IOException {
                    mCredential.initialize(request);
                    request.setLoggingEnabled(true);
//                request.setIOExceptionHandler(new HttpBackOffIOExceptionHandler(new ExponentialBackOff()));
                }
            };

            YouTube.Builder youtubeBuilder = new YouTube.Builder(transport, jsonFactory, initializer);
            youtubeBuilder.setApplicationName(mContext.getString(R.string.app_name));
//        youtubeBuilder.setYouTubeRequestInitializer(new YouTubeRequestInitializer(API_KEY));
            mService = youtubeBuilder.build();
        }

        @Override
        protected Void doInBackground(Uri... uris) {
            uploadYoutube(uris[0]);
            return null;
        }

        private String uploadYoutube(Uri data) {


            String PRIVACY_STATUS = "public"; // or public,private
            String PARTS = "snippet,status,contentDetails";

            String videoId = null;
            try {
                Video videoObjectDefiningMetadata = new Video();
                videoObjectDefiningMetadata.setStatus(new VideoStatus().setPrivacyStatus(PRIVACY_STATUS));

                VideoSnippet snippet = new VideoSnippet();
                snippet.setTitle("CALL YOUTUBE DATA API UNLISTED TEST " + System.currentTimeMillis());
                snippet.setDescription("MyDescription");
                snippet.setTags(Arrays.asList(new String[]{"TaG1,TaG2"}));
                videoObjectDefiningMetadata.setSnippet(snippet);

                YouTube.Videos.Insert videoInsert = mService.videos().insert(
                        PARTS,
                        videoObjectDefiningMetadata,
                        getMediaContent(getFileFromUri(data, (Activity) mContext)));/*.setOauthToken(token);*/

                MediaHttpUploader uploader = videoInsert.getMediaHttpUploader();
                uploader.setDirectUploadEnabled(false);

                MediaHttpUploaderProgressListener progressListener = new MediaHttpUploaderProgressListener() {
                    public void progressChanged(MediaHttpUploader uploader) throws IOException {
                        Log.d(TAG, "progressChanged: " + uploader.getUploadState());
                        switch (uploader.getUploadState()) {
                            case INITIATION_STARTED:
                                break;
                            case INITIATION_COMPLETE:
                                break;
                            case MEDIA_IN_PROGRESS:
                                Log.d(TAG, "progress: " + uploader.getProgress());

                                Bundle bundle = new Bundle();
                                bundle.putDouble("progress",uploader.getProgress());
                                Message message = new Message();
                                message.setData(bundle);
                                mHandler.sendMessage(message);
                                break;
                            case MEDIA_COMPLETE:
                            case NOT_STARTED:
                                Log.d(TAG, "progressChanged: upload_not_started");
                                break;
                        }
                    }
                };
                uploader.setProgressListener(progressListener);

                Log.d(TAG, "Uploading..");
                Video returnedVideo = videoInsert.execute();
                Log.d(TAG, "Video upload completed");
                videoId = returnedVideo.getId();
                mHandler.sendEmptyMessage(-1);
                Log.d(TAG, String.format("videoId = [%s]", videoId));
            } catch (final GooglePlayServicesAvailabilityIOException availabilityException) {
                Log.e(TAG, "GooglePlayServicesAvailabilityIOException", availabilityException);
            } catch (UserRecoverableAuthIOException e) {
                Log.i(TAG, String.format("UserRecoverableAuthIOException: %s",
                        e.getCause()));
                mView.handleUserRecoverableException(e.getIntent(),1001);

            } catch (IOException e) {
                Log.e(TAG, "IOException", e);
            }

            return videoId;

        }

        private AbstractInputStreamContent getMediaContent(File file) throws FileNotFoundException {
            InputStreamContent mediaContent = new InputStreamContent(
                    "video/*",
                    new BufferedInputStream(new FileInputStream(file)));
            mediaContent.setLength(file.length());

            return mediaContent;
        }

        private File getFileFromUri(Uri uri, Activity activity) {

            try {
                String filePath = null;

                String[] proj = {MediaStore.Video.VideoColumns.DATA};

                Cursor cursor = activity.getContentResolver().query(uri, proj, null, null, null);

                if (cursor.moveToFirst()) {
                    int column_index = cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DATA);
                    filePath = cursor.getString(column_index);
                }

                cursor.close();

                File file = new File(filePath);
                cursor.close();
                return file;
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }
    }


}
