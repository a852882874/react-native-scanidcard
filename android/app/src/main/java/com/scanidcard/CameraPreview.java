package com.scanidcard;

import android.content.Context;
import android.hardware.Camera;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * 相机预览接口
 * 继承SurfaceView并实现其Callback接口
 * SurfaceView是一个可以在主线程之外的线程向屏幕绘图的容器
 * Created by mmjbds999 on 2017/6/5.
 */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    private CameraManager cameraManager;//相机管理对象
    private Camera camera;//相机实例
    private String cameraType;//相机类型（back/front）
    private Camera.PictureCallback pictureCallback;//相机预览的回调

    private Handler autoFocusHandler;//自动对焦处理程序
    private boolean surfaceCreated;//绘图容器是否创建
    private boolean previewing = true;//是否开启预览
    private boolean autoFocus = true;//是否自动对焦

    private static final String TAG = "CameraPreview";//标记

    /**
     * 构造函数，需将相机预览回调对象传入
     * @param context
     * @param callback
     */
    public CameraPreview(Context context, Camera.PictureCallback callback) {
        super(context);

        cameraManager = new CameraManager();
        autoFocusHandler = new Handler();
        pictureCallback = callback;
        this.setZOrderOnTop(true);

//        setOnTouchListener(new OnTouchListener() {
//            @Override
//            public boolean onTouch(View v, MotionEvent event) {
//                if (camera == null)
//                    camera.startPreview();
//                return true;
//            }
//        });

    }

    /**
     * 启动相机
     */
    public void startCamera(){
        camera = cameraManager.getCamera(cameraType);
        startCameraPreview();
    }

    /**
     * 启动相机预览
     */
    public void startCameraPreview(){
        if(camera != null){
            try {
                previewing = true;
                getHolder().addCallback(this);
                camera.setPreviewDisplay(getHolder());
                camera.setDisplayOrientation(getDisplayOrientation());
//                camera.setPreviewCallback(pictureCallback);

                Camera.Parameters cp = camera.getParameters();

                int w = this.getLayoutParams().width;
                int h = this.getLayoutParams().height;

                cp.setPreviewSize(h,w);
//                cp.setPictureSize(h,w);

                camera.setParameters(cp);

                camera.startPreview();
                if(autoFocus){
                    if(surfaceCreated){
                        autoFocus();
                    }else{
                        scheduleAutoFocus();
                    }
                }
            }catch (Exception e){
                Log.e(TAG, e.toString(), e);
            }
        }
    }

    /**
     * 停止camera
     */
    public void stopCamera() {
        stopCameraPreview();
        cameraManager.releaseCamera();//释放相机资源
    }

    /**
     * 设置相机类型，先释放资源，再开启
     * @param cameraType
     */
    public void setCameraType(String cameraType) {
        this.cameraType = cameraType;
        stopCamera();
        startCamera();
    }

    public void takePic(){
        if (camera!=null){
            camera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    stopCamera();
                }
            });
        }
    }

    /**
     * 停止相机预览
     */
    public void stopCameraPreview() {
        if(camera != null) {
            try {
                previewing = false;
                getHolder().removeCallback(this);
                camera.cancelAutoFocus();
                camera.setOneShotPreviewCallback(null);
                camera.stopPreview();
            } catch(Exception e) {
                Log.e(TAG, e.toString(), e);
            }
        }
    }

    /**
     * 自动对焦
     */
    public void autoFocus(){
        try {
            camera.autoFocus(new Camera.AutoFocusCallback(){
                public void onAutoFocus(boolean success, Camera camera) {
                if(success){
                    camera.takePicture(null, null, pictureCallback);
                }
                scheduleAutoFocus();
                }
            });
        }catch (RuntimeException e){
            scheduleAutoFocus();
        }
    }

    /**
     * 延迟一秒自动对焦
     */
    private void scheduleAutoFocus() {
        autoFocusHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(camera != null && previewing && autoFocus && surfaceCreated) {
                    autoFocus();
                }
            }
        }, 1000);
    }

    /**
     * 获取相机的显示方向角度值
     * @return
     */
    public int getDisplayOrientation(){
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, info);
        WindowManager wm =  (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();

        this.setLayoutParams(new FrameLayout.LayoutParams(display.getWidth(), display.getWidth() * 3 / 5));

        int rotation = display.getRotation();//旋转值根据当前显示获取
        int degrees = 0;//角度根据当前Surface旋转值获取
        switch (rotation){
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        return result;
    }

    /**
     * 创建时触发事件
     * @param holder
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceCreated = true;
        startCamera();
    }

    /**
     * 大小发生改变时触发事件
     * @param holder
     * @param format
     * @param width
     * @param height
     */
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if(holder.getSurface() == null){
            return;
        }
        if(camera != null){
            try{
                camera.setDisplayOrientation(getDisplayOrientation());
            }catch (Exception e){
                Log.e(TAG, e.toString(), e);
            }
        }
    }

    /**
     * 销毁时触发事件，一般将绘图线程停止并释放
     * @param holder
     */
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopCamera();
    }

    /**
     * 设置flash动画
     * @param flag
     */
    public void setFlash(boolean flag) {
        if(camera != null && cameraManager.isFlashSupported(camera)) {
            Camera.Parameters parameters = camera.getParameters();
            if(flag) {
                if(parameters.getFlashMode().equals(Camera.Parameters.FLASH_MODE_TORCH)) {
                    return;
                }
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            } else {
                if(parameters.getFlashMode().equals(Camera.Parameters.FLASH_MODE_OFF)) {
                    return;
                }
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            }
            camera.setParameters(parameters);
        }
    }

}
