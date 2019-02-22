/*
 * Copyright 2018 Aletheia Ware LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aletheiaware.space.android;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.space.SpaceProto.Preview;
import com.aletheiaware.space.android.utils.SpaceAndroidUtils;
import com.aletheiaware.space.utils.SpaceUtils;
import com.google.protobuf.ByteString;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

public class UploadActivity extends AppCompatActivity {

    private EditText nameEditText;
    private TextView typeTextView;
    private TextView sizeTextView;
    private TextView contentTextView;
    private ImageView contentImageView;
    private VideoView contentVideoView;

    private Preview preview;
    private InputStream in;
    private FloatingActionButton fab;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SpaceAndroidUtils.createNotificationChannels(this);

        // Setup UI
        setContentView(R.layout.activity_upload);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.upload_toolbar);
        setSupportActionBar(toolbar);

        // Name EditText
        nameEditText = findViewById(R.id.upload_name);

        // Type TextView
        typeTextView = findViewById(R.id.upload_type);

        // Size TextView
        sizeTextView = findViewById(R.id.upload_size);

        // Content TextView
        contentTextView = findViewById(R.id.upload_text_view);

        // Content ImageView
        contentImageView = findViewById(R.id.upload_image_view);

        // Content VideoView
        contentVideoView = findViewById(R.id.upload_video_view);

        // FloatingActionButton
        fab = findViewById(R.id.upload_fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO disable fab and nameEditText until mining fails (if mining succeeds, activity will finish)
                //fab.setEnabled(false);
                //nameEditText.setEnabled(false);
                String name = nameEditText.getText().toString();
                String type = typeTextView.getText().toString();
                if (in != null) {
                    SpaceAndroidUtils.mine(UploadActivity.this, name, type, preview, in);
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (SpaceAndroidUtils.isInitialized()) {
            Intent intent = getIntent();
            if (intent != null) {
                String action = intent.getAction();
                if (action == null) {
                    action = "";
                }
                Log.d(SpaceUtils.TAG, "Action:" + action);
                Uri uri = intent.getData();
                if (uri == null) {
                    uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                }
                Log.d(SpaceUtils.TAG, "URI:" + uri);
                String type = intent.getType();
                Log.d(SpaceUtils.TAG, "Type:" + type);
                if (type == null && uri != null) {
                    type = getContentResolver().getType(uri);
                    Log.i(SpaceUtils.TAG, "Type: " + type);
                    if (type == null) {
                        type = SpaceUtils.getTypeByExtension(uri.toString());
                        Log.i(SpaceUtils.TAG, "Type: " + type);
                    }
                }
                if (type == null) {
                    type = SpaceUtils.UNKNOWN_TYPE;
                } else if (type.equals("image/*")) {
                    type = SpaceUtils.DEFAULT_IMAGE_TYPE;
                } else if (type.equals("video/*")) {
                    type = SpaceUtils.DEFAULT_VIDEO_TYPE;
                }
                typeTextView.setText(type);

                if (SpaceUtils.isText(type)) {
                    // Precomplete EditText with generated name
                    String generatedName = "Record" + System.nanoTime() + ".txt";
                    nameEditText.setText(generatedName);
                    String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                    if (text == null) {
                        text = "";
                    }
                    contentTextView.setText(text);
                    contentTextView.setVisibility(View.VISIBLE);
                    preview = Preview.newBuilder()
                            .setType(SpaceUtils.TEXT_PLAIN_TYPE)
                            .setData(ByteString.copyFromUtf8(text.substring(0, Math.min(text.length(), SpaceUtils.PREVIEW_TEXT_LENGTH))))
                            .build();
                    byte[] bytes = text.getBytes();
                    in = new ByteArrayInputStream(bytes);
                    sizeTextView.setText(BCUtils.sizeToString(bytes.length));
                    fab.setVisibility(View.VISIBLE);
                } else if (uri != null) {
                    nameEditText.setText(SpaceAndroidUtils.getName(this, uri));
                    try {
                        in = getContentResolver().openInputStream(uri);
                    } catch (IOException e) {
                        SpaceAndroidUtils.showErrorDialog(this, R.string.error_reading_uri, e);
                    }
                    if (in == null) {
                        contentTextView.setText("No Data");
                        contentTextView.setVisibility(View.VISIBLE);
                    } else {
                        try {
                            sizeTextView.setText(BCUtils.sizeToString(in.available()));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        Bitmap bitmap = null;
                        ByteArrayOutputStream os = new ByteArrayOutputStream();
                        if (SpaceUtils.isImage(type)) {
                            // ImageView
                            contentImageView.setImageURI(uri);
                            contentImageView.setVisibility(View.VISIBLE);
                            Bundle extras = intent.getExtras();
                            if (extras != null) {
                                bitmap = (Bitmap) extras.get(SpaceAndroidUtils.DATA_EXTRA);
                            }
                            if (bitmap == null) {
                                try {
                                    Bitmap image = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
                                    bitmap = ThumbnailUtils.extractThumbnail(image, SpaceUtils.PREVIEW_IMAGE_SIZE, SpaceUtils.PREVIEW_IMAGE_SIZE);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        } else if (SpaceUtils.isVideo(type)) {
                            // VideoView
                            contentVideoView.setVideoURI(uri);
                            contentVideoView.setVisibility(View.VISIBLE);
                            try {
                                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                                retriever.setDataSource(this, uri);
                                bitmap = retriever.getFrameAtTime();
                                retriever.release();
                            } catch (Exception e) {
                                /* Ignored */
                                e.printStackTrace();
                            }
                        }
                        if (bitmap != null) {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
                            preview = Preview.newBuilder()
                                    .setType(SpaceUtils.IMAGE_JPEG_TYPE)
                                    .setData(ByteString.copyFrom(os.toByteArray()))
                                    .setWidth(bitmap.getWidth())
                                    .setHeight(bitmap.getHeight())
                                    .build();
                        }
                        fab.setVisibility(View.VISIBLE);
                    }
                }
            }
        } else {
            Intent intent = new Intent(this, AccessActivity.class);
            startActivityForResult(intent, SpaceAndroidUtils.ACCESS_ACTIVITY);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case SpaceAndroidUtils.ACCESS_ACTIVITY:
                switch (resultCode) {
                    case RESULT_OK:
                        break;
                    case RESULT_CANCELED:
                        setResult(RESULT_CANCELED);
                        finish();
                        break;
                    default:
                        break;
                }
                break;
            case SpaceAndroidUtils.PAYMENT_ACTIVITY:
                switch (resultCode) {
                    case RESULT_OK:
                        final String name = nameEditText.getText().toString();
                        Log.d(SpaceUtils.TAG, "Name: " + name);
                        final String type = typeTextView.getText().toString();
                        Log.d(SpaceUtils.TAG, "Type: " + type);
                        final String alias = SpaceAndroidUtils.getAlias();
                        final String email = intent.getStringExtra(SpaceAndroidUtils.EMAIL_EXTRA);
                        Log.d(SpaceUtils.TAG, "Email: " + email);
                        final String paymentId = intent.getStringExtra(SpaceAndroidUtils.STRIPE_TOKEN_EXTRA);
                        Log.d(SpaceUtils.TAG, "PaymentId: " + paymentId);
                        new Thread() {
                            @Override
                            public void run() {
                                try {
                                    SpaceUtils.subscribe(alias, email, paymentId);
                                    SpaceAndroidUtils.mine(UploadActivity.this, name, type, preview, in);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (NoSuchAlgorithmException e) {
                                    e.printStackTrace();
                                } catch (InvalidKeyException e) {
                                    e.printStackTrace();
                                } catch (SignatureException e) {
                                    e.printStackTrace();
                                }
                            }
                        }.start();
                        break;
                    case RESULT_CANCELED:
                        break;
                    default:
                        break;
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, intent);
                break;
        }
    }
}
