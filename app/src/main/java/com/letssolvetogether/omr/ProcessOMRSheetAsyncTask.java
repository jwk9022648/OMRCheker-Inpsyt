package com.letssolvetogether.omr;

import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncTask;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.cameraview.CameraView;
import com.letssolvetogether.omr.detection.DetectionUtil;
import com.letssolvetogether.omr.drawing.DrawingUtil;
import com.letssolvetogether.omr.evaluation.EvaluationUtil;
import com.letssolvetogether.omr.exceptions.UnsupportedCameraResolutionException;
import com.letssolvetogether.omr.object.OMRSheet;
import com.letssolvetogether.omr.object.OMRSheetCorners;
import com.letssolvetogether.omr.utils.PrereqChecks;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

public class ProcessOMRSheetAsyncTask extends AsyncTask<Void, Void, Boolean> {

    private static String TAG="ProcessOMRSheetAsyncTask";

    private OMRSheet omrSheet;
    private OMRSheetCorners omrSheetCorners;
    private Bitmap bmpOMRSheet;
    private Mat matOMR;
    private CameraView mCameraView;
    //CustomView customView;
    private LinearLayout linearLayout;
    private ImageView iv;
    private DetectionUtil detectionUtil;
    private PrereqChecks prereqChecks;
    private byte[][] studentAnswers;
    private int score;

    public ProcessOMRSheetAsyncTask(CameraView mCameraView, OMRSheet omrSheet) {
        this.omrSheet = omrSheet;
        this.mCameraView = mCameraView;

        detectionUtil = new DetectionUtil(omrSheet);
        prereqChecks = new PrereqChecks();

        linearLayout = new LinearLayout(mCameraView.getContext());
        iv = new ImageView(mCameraView.getContext());
        iv.setAdjustViewBounds(true);
        linearLayout.addView(iv);
    }

    @Override
    protected Boolean doInBackground(Void... voids) { //카메라가 켜져있는동안 백그라운드 연속 동작
        Log.i(TAG, "doInBackground");

        bmpOMRSheet = this.mCameraView.getPreviewFrame();

        boolean isBlurry = prereqChecks.isBlurry(bmpOMRSheet);
        if(isBlurry) {
            mCameraView.requestPreviewFrame();
            return false;
        }

        matOMR = new Mat();
        Utils.bitmapToMat(bmpOMRSheet,matOMR);
        omrSheetCorners = detectionUtil.detectOMRSheetCorners(matOMR); //코너추정
        return true; //기준 원들의 위치를 찾았다면 onPostExcute
    }

    @Override
    protected void onPostExecute(Boolean result) {
        Log.i(TAG, "onPostExecute");
        if(omrSheetCorners == null){
            mCameraView.requestPreviewFrame();
        }else{

            omrSheet.setMatOMRSheet(matOMR);
            omrSheet.setWidth(matOMR.cols());
            omrSheet.setHeight(matOMR.rows());
            omrSheet.setOmrSheetBlock();
            omrSheet.setOmrSheetCorners(omrSheetCorners);

            Mat roiOfOMR = detectionUtil.findROIofOMR(omrSheet);
            if(matOMR == null){
                mCameraView.requestPreviewFrame();
            }
            omrSheet.setMatOMRSheet(roiOfOMR); //원 기준의 위치의 높이 너비를 구한다

            Bitmap bmp = Bitmap.createBitmap(roiOfOMR.cols(), roiOfOMR.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(roiOfOMR, bmp);
            omrSheet.setBmpOMRSheet(bmp); //여기까지 왔다면 bmp변수에 자르고 늘린 가공된 사진이 저장됨

            try {
                studentAnswers = detectionUtil.getStudentAnswers(roiOfOMR); //가공된 OMR카드에서 본격적으로 마킹된 부분 판별
            } catch (UnsupportedCameraResolutionException e) {
                AlertDialog.Builder dialogUnsupporteCameraResolution = new AlertDialog.Builder(mCameraView.getContext());

                dialogUnsupporteCameraResolution.setMessage("The Camera resolution "+roiOfOMR.rows()+"x"+matOMR.cols()+" is not supported by OMR Checker.\nPlease take screenshot and send a mail to shreyaspatel29@gmail.com");
                dialogUnsupporteCameraResolution.setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                dialogUnsupporteCameraResolution.show();
                return;
            }

            //score = new EvaluationUtil(omrSheet).calculateScore(studentAnswers, omrSheet.getCorrectAnswers());
            score =1;
            new DrawingUtil(omrSheet).drawRectangle(studentAnswers);

            final AlertDialog.Builder dialogOMRSheetDisplay = new AlertDialog.Builder(mCameraView.getContext());

            //set custom title to dialog box
            TextView textView = new TextView(mCameraView.getContext());
            textView.setText(" Score: " + score);
            textView.setTextColor(Color.BLACK);
            textView.setTextSize(30);

            //set OMR Sheet to be displayed in dialog
            iv.setImageBitmap(omrSheet.getBmpOMRSheet());

            dialogOMRSheetDisplay.setCustomTitle(textView);
            dialogOMRSheetDisplay.setView(linearLayout);
            dialogOMRSheetDisplay.setNeutralButton("Go for next OMR", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dlg, int sumthin) {
                }
            });

            dialogOMRSheetDisplay.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialogInterface) {
                    mCameraView.requestPreviewFrame();
                }
            });

            dialogOMRSheetDisplay.show();
            Log.i(TAG,"DONE");

        }
    }
}