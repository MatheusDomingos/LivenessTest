package com.insoniadigital.livenesstest;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.tensorflow.lite.Interpreter;
import org.w3c.dom.Text;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.DecimalFormat;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static int  GALLERY_REQUEST = 98;
    private static int  CAMERA_PIC_REQUEST = 99;

    private ImageView ivPhoto;
    private TextView tvConfidence;

    private  Button btChoose;
    private  Button btTake;

    protected Interpreter tflite;

    private static final int MAX_RESULTS = 3;
    private static final int BATCH_SIZE = 1;
    private static final int PIXEL_SIZE = 3;
    private static final float THRESHOLD = 0.1f;

    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;

    private boolean quant = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initUI();
        initAction();

        // Carregar modelo
        try {
            tflite = new Interpreter(loadModelFile(this));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void initAction (){
        btChoose.setOnClickListener(this);
        btTake.setOnClickListener(this);
    }

    private void  initUI() {
        btChoose  = findViewById(R.id.btChoose);
        btTake  = findViewById(R.id.btTake);
        ivPhoto = findViewById(R.id.iv_photo);
        tvConfidence = findViewById(R.id.tv_confidence);
    }

    /** Memory-map the model file in Assets. */
    private MappedByteBuffer loadModelFile(Activity activity) throws IOException {

        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd("perto.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);

    }


    private ByteBuffer convertBitmapToByteBuffer(Bitmap pBitmap) {

        Bitmap bitmap = Bitmap.createScaledBitmap(pBitmap, 224, 224, false);

        ByteBuffer byteBuffer;

        int inputSize =  224;

        if(quant) {
            byteBuffer = ByteBuffer.allocateDirect(BATCH_SIZE * inputSize * inputSize * PIXEL_SIZE);
        } else {
            byteBuffer = ByteBuffer.allocateDirect(4 * BATCH_SIZE * inputSize * inputSize * PIXEL_SIZE);
        }

        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[inputSize * inputSize];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int pixel = 0;
        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                final int val = intValues[pixel++];
                if(quant){
                    byteBuffer.put((byte) ((val >> 16) & 0xFF));
                    byteBuffer.put((byte) ((val >> 8) & 0xFF));
                    byteBuffer.put((byte) (val & 0xFF));
                } else {
                    byteBuffer.putFloat((((val >> 16) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                    byteBuffer.putFloat((((val >> 8) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                    byteBuffer.putFloat((((val) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                }

            }
        }
        return byteBuffer;
    }

    private void proccessImage(Bitmap bitmap){

        ByteBuffer byteBuffer = convertBitmapToByteBuffer(bitmap);

        if(quant){
            byte[][] result = new byte[1][2];
            tflite.run(byteBuffer, result);
            System.out.println(result);;
        } else {
            float [][] result = new float[1][2];
            tflite.run(byteBuffer, result);
            float[] resultLiveness = result[0];
            float confidenceBoa = resultLiveness[0];
            float confidenceFotodefoto = resultLiveness[1];

            DecimalFormat df = new DecimalFormat("#.##");

            String strFotoboa =  df.format(confidenceBoa);
            String strFotodefoto =  df.format(confidenceFotodefoto);

            tvConfidence.setText("Confidence Fotoboa: " + strFotoboa + " Confidence fotodefoto " + strFotodefoto);
        }

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == Activity.RESULT_OK) {

            if(requestCode == GALLERY_REQUEST) {

                Uri selectedImage = data.getData();
                try {

                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImage);
                    ivPhoto.setImageBitmap(bitmap);
                    proccessImage(bitmap);

                } catch (IOException e) {
                    Log.i("TAG", "Some exception " + e);
                }

            }else if(requestCode == CAMERA_PIC_REQUEST) {

                if (requestCode == CAMERA_PIC_REQUEST) {
                    Bitmap bitmap = (Bitmap) data.getExtras().get("data");
                    ivPhoto.setImageBitmap(bitmap);
                    proccessImage(bitmap);

                }

            }

        }
    }

    @Override
    public void onClick(View view) {
        if(view == btChoose) {
            // Abrir galeria
            Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
            photoPickerIntent.setType("image/*");
            startActivityForResult(photoPickerIntent, GALLERY_REQUEST);
        }else if(view == btTake) {
            // Abrir camera
            Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            startActivityForResult(cameraIntent, CAMERA_PIC_REQUEST);
        }
    }
}
