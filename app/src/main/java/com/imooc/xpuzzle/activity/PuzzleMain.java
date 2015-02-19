package com.imooc.xpuzzle.activity;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

import com.imooc.xpuzzle.R;
import com.imooc.xpuzzle.adapter.GridItemsAdapter;
import com.imooc.xpuzzle.bean.ItemBean;
import com.imooc.xpuzzle.util.GameUtil;
import com.imooc.xpuzzle.util.ImagesUtil;
import com.imooc.xpuzzle.util.ScreenUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 拼图逻辑主界面：面板显示
 *
 * @author xys
 */
public class PuzzleMain extends Activity implements OnClickListener {

    // 拼图完成时显示的最后一个图片
    public static Bitmap lastBitmap;
    // 设置为N*N显示
    public static int type = 2;
    // 步数显示
    public static int countIndex = 0;
    // 计时显示
    public static int timerIndex = 0;
    /**
     * UI更新Handler
     */
    private Handler handler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    // 更新计时器
                    timerIndex++;
                    tv_Timer.setText("" + timerIndex);
                    break;
                default:
                    break;
            }
        }
    };
    // 选择的图片
    private Bitmap picSelected;
    // PuzzlePanel
    private GridView gv_puzzle_main_detail;
    private int resId;
    private String picPath;
    private ImageView imageView;
    // Button
    private Button btnBack;
    private Button btnImage;
    private Button btnRestart;
    // 显示步数
    private TextView tv_puzzle_main_counts;
    // 计时器
    private TextView tv_Timer;
    // 切图后的图片
    private List<Bitmap> bitmapItemLists = new ArrayList<Bitmap>();
    // GridView适配器
    private GridItemsAdapter adapter;
    // Flag 是否已显示原图
    private boolean isShowImg;
    // 计时器类
    private Timer timer;
    /**
     * 计时器线程
     */
    private TimerTask timerTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.xpuzzle_puzzle_detail_main);
        // 获取选择的图片
        Bitmap picSelectedTemp;
        // 选择默认图片还是自定义图片
        resId = getIntent().getExtras().getInt("picSelectedID");
        picPath = getIntent().getExtras().getString("picPath");
        if (resId != 0) {
            picSelectedTemp = BitmapFactory.decodeResource(getResources(), resId);
        } else {
            picSelectedTemp = BitmapFactory.decodeFile(picPath);
        }
        type = getIntent().getExtras().getInt("type", 2);
        // 对图片处理
        handlerImage(picSelectedTemp);
        // 初始化Views
        initViews();
        // 生成游戏数据
        generateGame();
        // GridView点击事件
        gv_puzzle_main_detail.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View view, int position, long arg3) {
                // 判断是否可移动
                if (GameUtil.isMoveable(position)) {
                    // 交换点击Item与空格的位置
                    GameUtil.swapItems(GameUtil.itemBeans.get(position), GameUtil.blankItemBean);
                    // 重新获取图片
                    recreateData();
                    // 通知GridView更改UI
                    adapter.notifyDataSetChanged();
                    // 更新步数
                    countIndex++;
                    tv_puzzle_main_counts.setText("" + countIndex);
                    // 判断是否成功
                    if (GameUtil.isSuccess()) {
                        // 将最后一张图显示完整
                        recreateData();
                        bitmapItemLists.remove(type * type - 1);
                        bitmapItemLists.add(lastBitmap);
                        // 通知GridView更改UI
                        adapter.notifyDataSetChanged();
                        Toast.makeText(PuzzleMain.this, "拼图成功!", Toast.LENGTH_LONG).show();
                        gv_puzzle_main_detail.setEnabled(false);
                        timer.cancel();
                        timerTask.cancel();
                    }
                }
            }
        });
        // 返回按钮点击事件
        btnBack.setOnClickListener(this);
        // 显示原图按钮点击事件
        btnImage.setOnClickListener(this);
        // 重置按钮点击事件
        btnRestart.setOnClickListener(this);
    }

    /**
     * Button点击事件
     */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            // 返回按钮点击事件
            case R.id.btn_puzzle_main_back:
                PuzzleMain.this.finish();
                break;
            // 显示原图按钮点击事件
            case R.id.btn_puzzle_main_img:
                Animation animShow = AnimationUtils.loadAnimation(PuzzleMain.this, R.anim.image_show_anim);
                Animation animHide = AnimationUtils.loadAnimation(PuzzleMain.this, R.anim.image_hide_anim);
                if (isShowImg) {
                    imageView.startAnimation(animHide);
                    imageView.setVisibility(View.GONE);
                    isShowImg = false;
                } else {
                    imageView.startAnimation(animShow);
                    imageView.setVisibility(View.VISIBLE);
                    isShowImg = true;
                }
                break;
            // 重置按钮点击事件
            case R.id.btn_puzzle_main_restart:
                cleanConfig();
                generateGame();
                recreateData();
                // 通知GridView更改UI
                tv_puzzle_main_counts.setText("" + countIndex);
                adapter.notifyDataSetChanged();
                gv_puzzle_main_detail.setEnabled(true);
                break;
            default:
                break;
        }
    }

    /**
     * 生成游戏数据
     */
    private void generateGame() {
        // 切图 获取初始拼图数据 正常顺序
        new ImagesUtil().createInitBitmaps(type, picSelected, PuzzleMain.this);
        // 生成随机数据
        GameUtil.getPuzzleGenerator();
        // 获取Bitmap集合
        for (ItemBean temp : GameUtil.itemBeans) {
            bitmapItemLists.add(temp.getBitmap());
        }

        // 数据适配器
        adapter = new GridItemsAdapter(this, bitmapItemLists);
        gv_puzzle_main_detail.setAdapter(adapter);

        // 启用计时器
        timer = new Timer(true);
        // 计时器线程
        timerTask = new TimerTask() {

            @Override
            public void run() {
                Message msg = new Message();
                msg.what = 1;
                handler.sendMessage(msg);
            }
        };
        // 每1000ms执行 延迟0s
        timer.schedule(timerTask, 0, 1000);
    }

    /**
     * 添加显示原图的View
     */
    private void addImgView() {
        RelativeLayout relativeLayout = (RelativeLayout) findViewById(R.id.rl_puzzle_main_main_layout);
        imageView = new ImageView(PuzzleMain.this);
        imageView.setImageBitmap(picSelected);
        int x = (int) (picSelected.getWidth() * 0.9F);
        int y = (int) (picSelected.getHeight() * 0.9F);
        LayoutParams params = new LayoutParams(x, y);
        params.addRule(RelativeLayout.CENTER_IN_PARENT);
        imageView.setLayoutParams(params);
        relativeLayout.addView(imageView);
        imageView.setVisibility(View.GONE);
    }

    /**
     * 返回时调用
     */
    @Override
    protected void onStop() {
        super.onStop();
        // 清空相关参数设置
        cleanConfig();
        this.finish();
    }

    /**
     * 清空相关参数设置
     */
    private void cleanConfig() {
        // 清空相关参数设置
        GameUtil.itemBeans.clear();
        // 停止计时器
        timer.cancel();
        timerTask.cancel();
        countIndex = 0;
        timerIndex = 0;
        // 清除拍摄的照片
        if (picPath != null) {
            // 删除照片
            File file = new File(MainActivity.TEMP_IMAGE_PATH);
            if (file.exists()) {
                file.delete();
            }
        }
    }

    /**
     * 重新获取图片
     */
    private void recreateData() {
        bitmapItemLists.clear();
        for (ItemBean temp : GameUtil.itemBeans) {
            bitmapItemLists.add(temp.getBitmap());
        }
    }

    /**
     * 对图片处理 自适应大小
     *
     * @param bitmap
     */
    private void handlerImage(Bitmap bitmap) {
        // 将图片放大到固定尺寸
        int screenWidth = ScreenUtil.getScreenSize(this).widthPixels;
        int screenHeigt = ScreenUtil.getScreenSize(this).heightPixels;
        picSelected = new ImagesUtil().resizeBitmap(screenWidth * 0.8f, screenHeigt * 0.6f, bitmap);
    }

    /**
     * 初始化Views
     */
    private void initViews() {
        // Button
        btnBack = (Button) findViewById(R.id.btn_puzzle_main_back);
        btnImage = (Button) findViewById(R.id.btn_puzzle_main_img);
        btnRestart = (Button) findViewById(R.id.btn_puzzle_main_restart);
        // Flag 是否已显示原图
        isShowImg = false;

        // GV
        gv_puzzle_main_detail = (GridView) findViewById(R.id.gv_puzzle_main_detail);
        // 设置为N*N显示
        gv_puzzle_main_detail.setNumColumns(type);
        LayoutParams gridParams = new LayoutParams(picSelected.getWidth(), picSelected.getHeight());
        // 水平居中
        gridParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        // 其他格式属性
        gridParams.addRule(RelativeLayout.BELOW, R.id.ll_puzzle_main_spinner);
        // Grid显示
        gv_puzzle_main_detail.setLayoutParams(gridParams);
        gv_puzzle_main_detail.setHorizontalSpacing(0);
        gv_puzzle_main_detail.setVerticalSpacing(0);

        // TV步数
        tv_puzzle_main_counts = (TextView) findViewById(R.id.tv_puzzle_main_counts);
        tv_puzzle_main_counts.setText("" + countIndex);
        // TV计时器
        tv_Timer = (TextView) findViewById(R.id.tv_puzzle_main_time);
        tv_Timer.setText("0秒");

        // 添加显示原图的View
        addImgView();
    }
}
