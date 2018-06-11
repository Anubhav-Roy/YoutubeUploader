package com.youtube.anubhav.youtubeuploader.Contracts;

import android.content.Intent;
import android.net.Uri;

public interface HomeContract {

    interface View {

        public void showGooglePlayServicesAvailabilityErrorDialog( final int connectionStatusCode);

        void chooseAccount();

        void initVideoPicker();

        void handleUserRecoverableException(Intent i , int RESULT_CODE );

        void showProgressDialog();

        void updateProgressDialog(Double progress);


    }

    interface Presenter {

        void getResultsFromApi();

        void setSelectedAccountName(String accountName);

        void startUpload(Uri uri);
    }
}
