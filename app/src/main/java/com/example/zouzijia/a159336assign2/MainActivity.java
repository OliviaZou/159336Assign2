package com.example.zouzijia.a159336assign2;


import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.LruCache;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity
{
    private final static int READ_EXTERNAL_STORAGE_PERMISSION_RESULT = 0;
    private final static int MEDIASTORE_LOADER_ID = 0;

    private static final int NCOLS = 4;
    //?x? grid of image photos
    private GridView mPhotos;
    //Adapter to provide photo data for the GridView
    private PhotoAdapter mPhotoAdapter;

    private LruCache<String,Bitmap> mCache;
    private static final int CACHE_SIZE = (int)(Runtime.getRuntime().maxMemory()/1024/16);
    private Cursor mCursor;
    // for pinch to zoom
    private ScaleGestureDetector mScaleGestureDetector;

    private String[] projection = {
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DATE_ADDED
    };

    private ArrayList<String> mPhotoFile;

    public void init(){
        mCursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,projection,
                null,null,MediaStore.Images.Media.DATE_ADDED +" DESC");
        mPhotoFile = new ArrayList<>();
        int index = mCursor.getColumnIndex(MediaStore.Images.Media.DATA);
        while(mCursor.moveToNext()){
            mPhotoFile.add(mCursor.getString(index));
        }
        mCursor.close();
        mCache = new LruCache<String,Bitmap>(CACHE_SIZE){
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
        //set the number of columns in the grid
        mPhotos = (GridView) findViewById(R.id.gridView);
        mPhotos.setNumColumns(NCOLS);
        // and the adapter for photo data
        mPhotoAdapter = new PhotoAdapter();
        mPhotos.setAdapter(mPhotoAdapter);
        //when a photo is clicked
        mPhotos.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                openPhotoDetailActivity(mPhotoFile.get(position));
                //create an new activity for single photo
            }
        });
        // for pinch to zoom
        mScaleGestureDetector=new ScaleGestureDetector(this,new ScaleGestureDetector.OnScaleGestureListener(){
            // must be a float so it knows if we are half way between integer values
            private  float mCols = NCOLS;
            // not used
            @Override
            public void onScaleEnd(ScaleGestureDetector detector){

            }
            // not used
            @Override
            public  boolean onScaleBegin(ScaleGestureDetector detector) {
                return true;
            }
            // change the columns if necessary
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                mCols = mCols/ detector.getScaleFactor();
                if(mCols<1)
                    mCols=1;
                if(mCols>8)
                    mCols=8;
                mPhotos.setNumColumns((int)mCols);
                //recalculate the tile heights
                for(int i=0;i<mPhotos.getChildCount();i++) {
                    if(mPhotos.getChildAt(i)!=null) {
                        mPhotos.getChildAt(i).setMinimumHeight((int)(mPhotos.getWidth()/(int)(mCols)));
                    }
                }
                // make sure it's redrawn
                mPhotos.invalidate();
                return true;
            }
        });
        // call the ScaleGestureDetector when the view is touched
        mPhotos.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                mScaleGestureDetector.onTouchEvent(motionEvent);
                return false;

            }
        });
    }


    /**
     * Adapter class
     */
    public class PhotoAdapter extends BaseAdapter {

        class ViewHolder{
            int position;
            ImageView photo;
        }
        @Override
        public int getCount() {
            return mPhotoFile.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            final ViewHolder viewHolder;
            if(convertView == null){
                convertView = getLayoutInflater().inflate(R.layout.thumbnail,null);
                viewHolder = new ViewHolder();
                viewHolder.photo = convertView.findViewById(R.id.thumbnail_image);
                convertView.setTag(viewHolder);
            }else{
                viewHolder = (ViewHolder) convertView.getTag();
            }

            int width = mPhotos.getWidth()/mPhotos.getNumColumns();
            viewHolder.photo.setLayoutParams(new LinearLayout.LayoutParams(width,width));
            viewHolder.photo.setImageBitmap(null);
            viewHolder.position = position;
            String key = String.valueOf(viewHolder.position);
            Bitmap thumbnail = mCache.get(key);
            if(thumbnail!= null){
                viewHolder.photo.setImageBitmap(thumbnail);
            }else {
                new AsyncTask<ViewHolder,Void,Bitmap>(){
                    private ViewHolder vh;

                    @Override
                    protected Bitmap doInBackground(ViewHolder... viewHolders) {
                        vh = viewHolders[0];
                        String path = mPhotoFile.get(position);
                        if(position!=vh.position){
                            return null;
                        }
                        BitmapFactory.Options op = new BitmapFactory.Options();
                        op.inSampleSize = 16;
                        Bitmap thumbnail = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(path,op),200,200);
                        String key = String.valueOf(position);
                        mCache.put(key,thumbnail);
                        return thumbnail;
                    }

                    @Override
                    protected void onPostExecute(Bitmap bitmap) {
                        if(vh.position == position){
                            vh.photo.setImageBitmap(bitmap);
                        }
                    }
                }.execute(viewHolder);
            }
            return convertView;
        }
    }

    public void openPhotoDetailActivity(String path) {

        Intent openPhotoDetailIntent = new Intent(this,full_images.class);

        openPhotoDetailIntent.putExtra("PHOTO_DETIAL_PATH",path);

        this.startActivity(openPhotoDetailIntent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if(Build.VERSION.SDK_INT >=23 &&ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED){
            init();
        }else{
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},1);
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode==1){
            if(grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this,"this Permission granted",Toast.LENGTH_SHORT).show();
            }else{
                Toast.makeText(this,"this Permission not granted",Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        init();
    }
}

