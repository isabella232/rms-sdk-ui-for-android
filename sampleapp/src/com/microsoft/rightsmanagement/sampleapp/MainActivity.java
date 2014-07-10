//
// Copyright � Microsoft Corporation, All Rights Reserved
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// THIS CODE IS PROVIDED *AS IS* BASIS, WITHOUT WARRANTIES OR CONDITIONS
// OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING WITHOUT LIMITATION
// ANY IMPLIED WARRANTIES OR CONDITIONS OF TITLE, FITNESS FOR A
// PARTICULAR PURPOSE, MERCHANTABILITY OR NON-INFRINGEMENT.
//
// See the Apache License, Version 2.0 for the specific language
// governing permissions and limitations under the License.

package com.microsoft.rightsmanagement.sampleapp;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import com.microsoft.adal.Logger;
import com.microsoft.rightsmanagement.UserPolicy;
import com.microsoft.rightsmanagement.sampleapp.R;
import com.microsoft.rightsmanagement.sampleapp.MsipcTaskFragment.TaskStatus;
import com.microsoft.rightsmanagement.sampleapp.TextEditorFragment.TextEditorMode;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
/**
 * The Class MainActivity.
 */
public class MainActivity extends FragmentActivity implements TextEditorFragment.TextEditorFragmentEventListener,
        MsipcTaskFragment.TaskEventCallback, ProgressDialogFragment.ProgressDialogEventListener
{
    private MsipcTaskFragment mMsipcTaskFragment;
    private TextEditorFragment mTextEditorFragment;
    private Uri mUriOfFilePendingConsumption;
    private String TAG = "MainActivity";

    /*
     * (non-Javadoc)
     * @see
     * com.microsoft.rightsmanagement.sampleapp.TextEditorFragment.ITextEditorFragmentEventCallback#onBlankAreaClick()
     */
    @Override
    public void onBlankAreaClick()
    {
        mMsipcTaskFragment.showUserPolicy();
    }

    /*
     * (non-Javadoc)
     * @see
     * com.microsoft.rightsmanagement.sampleapp.ProgressDialogFragment.ProgressDialogEventListener#onCancelProgressDialog
     * (android.content.DialogInterface)
     */
    @Override
    public void onCancelProgressDialog(DialogInterface dialog)
    {
        if (mMsipcTaskFragment != null)
        {
            mMsipcTaskFragment.cancelTask();
        }
    }

    /*
     * (non-Javadoc)
     * @see
     * com.microsoft.rightsmanagement.sampleapp.MsipcTaskFragment.TaskEventCallback#onMsipcTaskUpdate(com.microsoft.
     * rightsmanagement.sampleapp.MsipcTaskFragment.TaskStatus)
     */
    @Override
    public void onMsipcTaskUpdate(TaskStatus taskStatus)
    {
        switch (taskStatus.getTaskState())
        {
            case Starting:
            case Running:
                App.displayProgressDialog(getSupportFragmentManager(), taskStatus.getMessage());
                break;
            case Completed:
                App.dismissProgressDialog(getSupportFragmentManager());
                break;
            case Cancelled:
            case Faulted:
                App.dismissProgressDialog(getSupportFragmentManager());
                App.displayMessageDialog(getSupportFragmentManager(), taskStatus.getMessage());
                break;
            default:
                break;
        }
        switch (taskStatus.getSignal())
        {
            case ContentConsumed:
                createTextEditorFragment(TextEditorMode.Enforced, mMsipcTaskFragment.getUserPolicy());
                mTextEditorFragment.setTextViewText(mMsipcTaskFragment.getDecryptedContent());
                mMsipcTaskFragment.showUserPolicy();
                break;
            case ContentProtected:
                App.sendFile(this, mMsipcTaskFragment.getProtectedContentFilePath());
            default:
                break;
        }
    }

    /*
     * (non-Javadoc)
     * @see
     * com.microsoft.rightsmanagement.sampleapp.TextEditorFragment.ITextEditorFragmentEventCallback#onProtectionButtonClick
     * ()
     */
    @Override
    public void onProtectionButtonClick()
    {
        mMsipcTaskFragment.startMsipcPolicyCreation(true);
    }

    /*
     * (non-Javadoc)
     * @see
     * com.microsoft.rightsmanagement.sampleapp.TextEditorFragment.ITextEditorFragmentEventCallback#onSendMailButtonClick
     * ()
     */
    @Override
    public void onSendMailButtonClick()
    {
        byte[] rawData = null;
        try
        {
            rawData = mTextEditorFragment.getTextViewText().getBytes("UTF-8");
            if(mTextEditorFragment.getUsePxtFileFormat())
            {
                mMsipcTaskFragment.startContentProtectionToPtxtFileFormat(rawData);
            }
            else
            {
                mMsipcTaskFragment.startContentProtectionToMyOwnProtectedTextFileFormat(rawData);
            }
        }
        catch (UnsupportedEncodingException e)
        {
            App.displayMessageDialog(getSupportFragmentManager(), e.getLocalizedMessage());
        }
    }

    /**
     * Get the result from OAuth activity, this either returns an error message or a parceled BoxAndroidClient.
     * 
     * @param requestCode the request code
     * @param resultCode the result code
     * @param data the data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        // handle ADAL results
        if (App.getInstance().getAuthenticationContext() != null)
        {
            App.getInstance().getAuthenticationContext().onActivityResult(requestCode, resultCode, data);
        }
        // handle MSIPC Results
        MsipcTaskFragment.handleMsipcUIActivityResult(requestCode, resultCode, data);
    }

    /*
     * (non-Javadoc)
     * @see android.support.v4.app.FragmentActivity#onCreate(android.os.Bundle)
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // instantiate/get reference on retain-able Fragment instance which hold IAsyncTask
        FragmentManager fragmentManager = getSupportFragmentManager();
        mMsipcTaskFragment = (MsipcTaskFragment)fragmentManager.findFragmentByTag(MsipcTaskFragment.TAG);
        // If the Fragment is not null then it is retained across a configuration change.
        if (mMsipcTaskFragment == null)
        {
            mMsipcTaskFragment = new MsipcTaskFragment();
            fragmentManager.beginTransaction().add(mMsipcTaskFragment, MsipcTaskFragment.TAG).commit();
        }
        mTextEditorFragment = (TextEditorFragment)getSupportFragmentManager().findFragmentByTag(TextEditorFragment.TAG);
        if (savedInstanceState == null)
        {
            // handle incoming intent
            Intent incommingIntent = getIntent();
            String action = incommingIntent.getAction();
            if ((action.compareTo(Intent.ACTION_VIEW) == 0) || (action.compareTo(Intent.ACTION_EDIT) == 0))
            {
                // Application state doesn't exist. intent is view.
                mUriOfFilePendingConsumption = incommingIntent.getData();
            }
            else if (action.compareTo(Intent.ACTION_MAIN) == 0)
            {
                createTextEditorFragment(TextEditorMode.NotEnforced, null);
            }
            else
            {
                throw new RuntimeException("shouldn't reach here");
            }
        }
    }

    /*
     * (non-Javadoc)
     * @see android.support.v4.app.FragmentActivity#onResumeFragments()
     */
    @Override
    protected void onResumeFragments()
    {
        super.onResumeFragments();
        // handle unposted tasks from mMsipcTaskFragment
        if (mMsipcTaskFragment != null)
        {
            TaskStatus latestUnpostedTaskStatus = mMsipcTaskFragment.getLatestUnpostedTaskStatus();
            if (latestUnpostedTaskStatus != null)
            {
                onMsipcTaskUpdate(latestUnpostedTaskStatus);
            }
        }
        // handle pending uri
        if (mUriOfFilePendingConsumption != null)
        {
            try
            {
                handleUriInput(mUriOfFilePendingConsumption);
            }
            catch (FileNotFoundException e)
            {
                App.displayMessageDialog(getSupportFragmentManager(), e.getLocalizedMessage());
            }
            finally
            {
                // uri was handled. There is no pending uri now
                mUriOfFilePendingConsumption = null;
            }
        }
    }

    /**
     * Creates the text editor fragment.
     * 
     * @param editorMode the editor mode
     */
    private void createTextEditorFragment(TextEditorMode editorMode, UserPolicy userPolicy)
    {
        int containerId = R.id.mainContainer;
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        if (mTextEditorFragment == null)
        {
            Logger.d(TAG , "mTextEditorFragment is null");
            mTextEditorFragment = TextEditorFragment.newInstance(editorMode, userPolicy);
            fragmentTransaction.add(containerId, mTextEditorFragment, TextEditorFragment.TAG);
            fragmentTransaction.commit();
        }
        else
        {
            Logger.d(TAG, "mTextEditorFragment is not null");
        }
    }

    /**
     * Handle URI input.
     * 
     * @param uri the uri
     * @throws FileNotFoundException the file not found exception
     */
    private void handleUriInput(Uri uri) throws FileNotFoundException
    {
        String originalFileName;
        // If the URI scheme is a content type, this means we must attempt to retrieve the file name from the
        // content provider service.
        if (uri.getScheme().toString().equals("content"))
        {
            originalFileName = App.getFileNameFromContent(this, uri);
        }
        else
        {
            originalFileName = uri.getLastPathSegment().toString();
        }
        if (App.isPTxtFile(originalFileName))
        {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            mMsipcTaskFragment.startContentConsumptionFromPtxtFileFormat(inputStream);
        }
        else if (App.isTxt2File(originalFileName))
        {   
            InputStream inputStream = getContentResolver().openInputStream(uri);
            mMsipcTaskFragment.startContentConsumptionFromMyOwnProtectedTextFileFormat(inputStream);
        }
    }
}