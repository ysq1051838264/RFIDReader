package com.thingmagic.rfidreader.Listener;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.thingmagic.Gen2;
import com.thingmagic.ReadExceptionListener;
import com.thingmagic.ReadListener;
import com.thingmagic.Reader;
import com.thingmagic.ReaderException;
import com.thingmagic.SimpleReadPlan;
import com.thingmagic.TagOp;
import com.thingmagic.TagProtocol;
import com.thingmagic.TagReadData;
import com.thingmagic.rfidreader.R;
import com.thingmagic.rfidreader.ReaderActivity;
import com.thingmagic.rfidreader.TagRecord;
import com.thingmagic.rfidreader.services.SettingsService;
import com.thingmagic.util.ExcelUtils;
import com.thingmagic.util.LoggerUtil;
import com.thingmagic.util.Utilities;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceListener implements View.OnClickListener {

    private static String TAG = "ServiceListener";
    private static EditText ntReaderField;
    private static EditText customReaderField;
    private static Spinner serialList = null;
    private static LinearLayout servicelayout;
    private static RadioGroup readerRadioGroup = null;
    private static RadioButton serialReaderRadioButton = null;
    private static RadioButton networkReaderRadioButton = null;
    private static RadioButton customReaderRadioButton = null;
    private static RadioButton syncReadRadioButton = null;
    private static RadioButton asyncReadSearchRadioButton = null;
    private static Button readButton = null;
    private static Button connectButton = null;
    private static TextView searchResultCount = null;
    private static TextView readTimeView = null;
    private static TextView totalTagCountView = null;

    private static TextView unique_tag_count = null;
    private static TextView total_tag_count = null;
    private static TextView time_taken = null;

    private static ProgressBar progressBar = null;
    private static int redColor = 0xffff0000;
    private static int textColor = 0xff000000;
    private static ReadThread readThread;
    private static TableLayout table;
    private static LayoutInflater inflater;
    private static ArrayList<String> addedEPCRecords = new ArrayList<String>();

    private static ConcurrentHashMap<String, TagRecord> epcToReadDataMap = new ConcurrentHashMap<String, TagRecord>();
    private static int uniqueRecordCount = 0;
    private static int totalTagCount = 0;
    private static long queryStartTime = 0;
    private static long queryStopTime = 0;

    private static ReaderActivity mReaderActivity;
    private static SettingsService mSettingsService;
    private static Timer timer = new Timer();

    public ServiceListener(ReaderActivity readerActivity) {
        mReaderActivity = readerActivity;
        mSettingsService = new SettingsService(mReaderActivity);
        findAllViewsById();
    }

    private void findAllViewsById() {
        syncReadRadioButton = (RadioButton) mReaderActivity
                .findViewById(R.id.SyncRead_radio_button);
        asyncReadSearchRadioButton = (RadioButton) mReaderActivity
                .findViewById(R.id.AsyncRead_radio_button);
        readButton = (Button) mReaderActivity.findViewById(R.id.Read_button);
        connectButton = (Button) mReaderActivity
                .findViewById(R.id.Connect_button);
        searchResultCount = (TextView) mReaderActivity
                .findViewById(R.id.search_result_view);
        totalTagCountView = (TextView) mReaderActivity
                .findViewById(R.id.totalTagCount_view);
        progressBar = (ProgressBar) mReaderActivity
                .findViewById(R.id.progressbar);
        textColor = searchResultCount.getTextColors().getDefaultColor();
        table = (TableLayout) mReaderActivity.findViewById(R.id.tablelayout);
        inflater = (LayoutInflater) mReaderActivity
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ntReaderField = (EditText) mReaderActivity
                .findViewById(R.id.search_edit_text);
        customReaderField = (EditText) mReaderActivity
                .findViewById(R.id.custom_reader_field);
        serialList = (Spinner) mReaderActivity.findViewById(R.id.SerialList);
        serialReaderRadioButton = (RadioButton) mReaderActivity
                .findViewById(R.id.SerialReader_radio_button);
        networkReaderRadioButton = (RadioButton) mReaderActivity
                .findViewById(R.id.NetworkReader_radio_button);
        customReaderRadioButton = (RadioButton) mReaderActivity
                .findViewById(R.id.CustomReader_radio_button);
        servicelayout = (LinearLayout) mReaderActivity
                .findViewById(R.id.ServiceLayout);
        readerRadioGroup = (RadioGroup) mReaderActivity
                .findViewById(R.id.Reader_radio_group);
        readTimeView = (TextView) mReaderActivity.readOptions
                .findViewById(R.id.read_time_value);
        unique_tag_count = (TextView) mReaderActivity.performance_metrics
                .findViewById(R.id.unique_tag_count);
        total_tag_count = (TextView) mReaderActivity.performance_metrics
                .findViewById(R.id.total_tag_count);
        time_taken = (TextView) mReaderActivity.performance_metrics
                .findViewById(R.id.time_taken);
    }

    @Override
    public void onClick(View arg0) {
        try {
            String readerModel = (String) mReaderActivity.reader.paramGet("/reader/version/model");
            if (readerModel.equalsIgnoreCase("M6e Micro")
                    || readerModel.equalsIgnoreCase("sargas")) {
                SimpleReadPlan simplePlan = new SimpleReadPlan(new int[]{1, 2}, TagProtocol.GEN2);
                mReaderActivity.reader.paramSet("/reader/read/plan", simplePlan);
            } else if (readerModel.equalsIgnoreCase("M6e Nano")) {
                SimpleReadPlan simplePlan = new SimpleReadPlan(new int[]{1}, TagProtocol.GEN2);
                mReaderActivity.reader.paramSet("/reader/read/plan", simplePlan);
            }

            String operation = "";
            if (syncReadRadioButton.isChecked()) {
                operation = "syncRead";
                String readTimout = readTimeView.getText().toString();
                if (!Utilities.validateReadTimeout(searchResultCount,
                        readTimout)) {
                    return;
                }
                readButton.setText("Reading");
                readButton.setClickable(false);
            } else if (asyncReadSearchRadioButton.isChecked()) {
                operation = "asyncRead";
            }

            if (readButton.getText().equals("Stop Reading")) {
                readThread.setReading(false);
                readButton.setText("Stopping...");
                readButton.setClickable(false);
            } else if (readButton.getText().equals("Start Reading") || readButton.getText().equals("Reading")) {
                if (readButton.getText().equals("Start Reading")) {
                    readButton.setText("Stop Reading");
                }
                clearTagRecords();
                readThread = new ReadThread(mReaderActivity.reader, operation);
                readThread.execute();
            }
        } catch (Exception ex) {
            LoggerUtil.error(TAG, "Exception", ex);
        }
    }

    public OnClickListener clearListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            clearTagRecords();
        }
    };

    private File file;
    private String fileName;
    private static String[] title = {"次数", "epc", "tid"};
    public OnClickListener downListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            //下载
//            file = new File(getSDPath() + "/Record");
//            makeDir(file);

            file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/1/record/");
            if (!file.exists()) {
                if (!file.mkdirs()) {
                    Log.e("ysq", "创建缓存目录失败");
                }
            }

            ExcelUtils.initExcel(file.toString() + "/tid.xls", title);
            fileName = file.toString() + "/tid.xls";
            ExcelUtils.writeObjListToExcel(getRecordData(), fileName, v.getContext());
        }
    };

    private ArrayList<ArrayList<String>> getRecordData() {
        ArrayList<ArrayList<String>> recordList = new ArrayList<>();
        Set<String> keySet = epcToReadDataMap.keySet();
        for (String epcString : keySet) {
            TagRecord tagRecordData = epcToReadDataMap.get(epcString);
            ArrayList<String> beanList = new ArrayList<String>();
            beanList.add(tagRecordData.getReadCount() + "");
            beanList.add(tagRecordData.getEpcString());
            beanList.add(tagRecordData.getTid());
            recordList.add(beanList);
        }
        return recordList;
    }

    private String getSDPath() {
        File sdDir = null;
        boolean sdCardExist = Environment.getExternalStorageState().equals(
                android.os.Environment.MEDIA_MOUNTED);
        if (sdCardExist) {
            sdDir = Environment.getExternalStorageDirectory();
        }
        String dir = sdDir.toString();
        return dir;
    }

    public void makeDir(File dir) {
//        File dirPath = new File(Environment.getExternalStorageDirectory() + "/jpark/image/evaluate/");
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.e("ysq", "创建图片缓存目录失败");
            }
        }

//        if (!dir.getParentFile().exists()) {
//            makeDir(dir.getParentFile());
//        }
//        dir.mkdir();
    }

    public OnClickListener copyListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            //复制
            Set<String> keySet = epcToReadDataMap.keySet();
            StringBuilder sb = new StringBuilder("");
            for (String epcString : keySet) {
                TagRecord tagRecordData = epcToReadDataMap.get(epcString);
                String s = "    epc是:"+tagRecordData.getEpcString() + "    tid是:" + tagRecordData.getTid() + "\n";
                Log.e("ysq", s);
                sb.append(s);
            }

            //获取剪贴板管理器：
            ClipboardManager cm = (ClipboardManager) mReaderActivity.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData mClipData = ClipData.newPlainText("Label", sb.toString());
            cm.setPrimaryClip(mClipData);

            Toast.makeText(v.getContext(), "复制成功，请去粘贴~", Toast.LENGTH_SHORT).show();
        }
    };

    public static void clearTagRecords() {
        addedEPCRecords.clear();
        epcToReadDataMap.clear();
        table.removeAllViews();
        searchResultCount.setText("");
        totalTagCountView.setText("");
        uniqueRecordCount = 0;
        totalTagCount = 0;
        queryStartTime = System.currentTimeMillis();
    }

    public static class ReadThread extends
            AsyncTask<Void, Integer, ConcurrentHashMap<String, TagRecord>> {

        private String operation;
        private static boolean exceptionOccur = false;
        private static String exception = "";
        private static boolean reading = true;
        private static Reader mReader;
        private static TableRow fullRow = null;
        private static TextView nr = null;
        private static TextView epcValue = null;
        private static TextView dataView = null;
        private static TextView countView = null;
        private static boolean isEmbeddedRead = false;
        private static double timeTaken;
        private static long startTime;
        static ReadExceptionListener exceptionListener = new TagReadExceptionReceiver();
        static ReadListener readListener = new PrintListener();

        public ReadThread(Reader reader, String operation) {
            this.operation = operation;
            mReader = reader;
        }

        @Override
        protected void onPreExecute() {
            startTime = System.currentTimeMillis();
            clearTagRecords();
            syncReadRadioButton.setClickable(false);
            asyncReadSearchRadioButton.setClickable(false);
            connectButton.setEnabled(false);
            connectButton.setClickable(false);
            searchResultCount.setTextColor(textColor);
            searchResultCount.setText("Reading Tags....");
            progressBar.setVisibility(View.VISIBLE);
            addedEPCRecords = new ArrayList<String>();
            epcToReadDataMap = new ConcurrentHashMap<String, TagRecord>();
            exceptionOccur = false;

        }

        @Override
        protected ConcurrentHashMap<String, TagRecord> doInBackground(
                Void... params) {
            try {
                //加的读取tid
                int[] antennas = new int[]{1};
                TagOp op = new Gen2.ReadData(Gen2.Bank.TID, 0, (byte) 0);
                SimpleReadPlan plan = new SimpleReadPlan(antennas, TagProtocol.GEN2, null, op, 1000);
                mReader.paramSet("/reader/read/plan", plan);


                //mSettingsService.loadReadPlan(mReader);
                if (operation.equalsIgnoreCase("syncRead")) {
                    int timeOut = Integer.parseInt(readTimeView.getText()
                            .toString());

                    TagReadData[] tagReads = mReader.read(timeOut);
                    queryStopTime = System.currentTimeMillis();
                    for (TagReadData tr : tagReads) {
                        parseTag(tr, false);
                    }
                    publishProgress(0);
                } else {
                    setReading(true);
                    mReader.addReadExceptionListener(exceptionListener);
                    mReader.addReadListener(readListener);
                    mReader.startReading();
                    queryStartTime = System.currentTimeMillis();
                    refreshReadRate();
                    while (isReading()) {
                        /* Waiting till stop reading button is pressed */
                        Thread.sleep(5);
                    }
                    queryStopTime = System.currentTimeMillis();
                    if (!exceptionOccur) {
                        mReader.stopReading();
                        mReader.removeReadListener(readListener);
                        mReader.removeReadExceptionListener(exceptionListener);
                    }
                }
            } catch (Exception ex) {
                exception = ex.getMessage();
                exceptionOccur = true;
                LoggerUtil.error(TAG, "Exception while reading :", ex);
            }

            return epcToReadDataMap;
        }

        static class PrintListener implements ReadListener {
            public void tagRead(Reader r, final TagReadData tr) {
                readThread.parseTag(tr, true);
            }
        }

        // private static int connectionLostCount=0;
        static class TagReadExceptionReceiver implements ReadExceptionListener {
            public void tagReadException(Reader r, ReaderException re) {
                if (re.getMessage().contains("The module has detected high return loss")
                        || re.getMessage().contains("Tag ID buffer full")
                        || re.getMessage().contains("No tags found")) {
                    // exception = "No connected antennas found";
                    /* Continue reading */
                }
                // else if(re.getMessage().equals("Connection Lost"))
                // {
                // if(connectionLostCount == 3){
                // connectionLostCount = 0;
                // try {
                // r.connect();
                // } catch (Exception e) {
                // // TODO Auto-generated catch block
                // e.printStackTrace();
                // exception=re.getMessage();
                // exceptionOccur = true;
                // readThread.setReading(false);
                // readThread.publishProgress(-1);
                // }
                // }
                // connectionLostCount++;
                // }
                else {
                    Log.e(TAG, "Reader exception : ", re);
                    exception = re.getMessage();
                    exceptionOccur = true;
                    readThread.setReading(false);
                    readThread.publishProgress(-1);
                }
            }
        }

        private void refreshReadRate() {
            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    publishProgress(0);
                }
            }, 100, 300);
        }

        // private static void calculateReadrate()
        // {
        // long readRatePerSec = 0;
        // long elapsedTime = (System.currentTimeMillis() - queryStartTime) ;
        // if(!isReading()){
        // elapsedTime = queryStopTime- queryStartTime;
        // }
        //
        // long tagReadTime = elapsedTime/ 1000;
        // if(tagReadTime == 0)
        // {
        // readRatePerSec = (long) ((totalTagCount) / ((double) elapsedTime /
        // 1000));
        // }
        // else
        // {
        // readRatePerSec = (long) ((totalTagCount) / (tagReadTime));
        // }
        // }


        //byte 获取得到字符串
        public String tidString(byte[] data) {
            StringBuilder var1 = new StringBuilder(data.length * 2);

            for (int var2 = 0; var2 < data.length; ++var2) {
                var1.append(String.format("%02X", data[var2] & 255));
            }

            return new String(var1);
        }

        public byte[] getWrite() {
            String hexString = UUID.randomUUID().toString();
            hexString = hexString.toLowerCase();
            final byte[] byteArray = new byte[hexString.length() / 2];
            int k = 0;
            for (int i = 0; i < byteArray.length; i++) {//因为是16进制，最多只会占用4位，转换成字节需要两个16进制的字符，高位在先
                byte high = (byte) (Character.digit(hexString.charAt(k), 16) & 0xff);
                byte low = (byte) (Character.digit(hexString.charAt(k + 1), 16) & 0xff);
                byteArray[i] = (byte) (high << 4 | low);
                k += 2;
            }
            return byteArray;
        }

        private void parseTag(TagReadData tr, boolean publishResult) {
            //重写
//            Gen2.TagData epc = new Gen2.TagData(new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
//                                                 (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00});
//
//            byte[] b = getWrite();
//            Gen2.TagData epc2 = new Gen2.TagData(b);
//
//            Gen2.WriteTag tagop = new Gen2.WriteTag(epc);
//            TagData filter = new TagData(tr.getTag().epcBytes());
//
//            try {
//                mReader.executeTagOp(tagop, filter);
////                mReader.writeTag(filter,epc);
//            } catch (ReaderException e) {
//                e.printStackTrace();
//            }


            totalTagCount += tr.getReadCount();
            String epcString = tr.getTag().epcString();
            String tidString = tidString(tr.getData());

            Log.e("ysq得到tid是：", tidString);
            Log.e("ysq得到epc是：", epcString);
//
            if (epcToReadDataMap.keySet().contains(epcString)) {
                TagRecord tempTR = epcToReadDataMap.get(epcString);
                tempTR.readCount += tr.getReadCount();
            } else {
                TagRecord tagRecord = new TagRecord();
                tagRecord.setEpcString(epcString);
                tagRecord.setReadCount(tr.getReadCount());
                tagRecord.setTid(tidString);
                epcToReadDataMap.put(epcString, tagRecord);
            }
        }

        //作用：在主线程 显示线程任务执行的进度
        @Override
        protected void onProgressUpdate(Integer... progress) {
            int progressToken = progress[0];
            if (progressToken == -1) {
                searchResultCount.setTextColor(redColor);
                searchResultCount.setText("ERROR :" + exception);
                totalTagCountView.setText("");
            } else {
                populateSearchResult(epcToReadDataMap);
                if (!exceptionOccur && totalTagCount > 0) {
                    searchResultCount.setTextColor(textColor);
                    searchResultCount.setText(Html
                            .fromHtml("<b>Unique Tags :</b> "
                                    + epcToReadDataMap.keySet().size()));
                    totalTagCountView.setText(Html
                            .fromHtml("<b>Total Tags  :</b> " + totalTagCount));
                }
            }
        }

        //作用：接收线程任务执行结果、将执行结果显示到UI组件
        @Override
        protected void onPostExecute(
                ConcurrentHashMap<String, TagRecord> epcToReadDataMap) {
            timer.cancel();
            if (exceptionOccur) {
                searchResultCount.setTextColor(redColor);
                searchResultCount.setText("ERROR :" + exception);
                totalTagCountView.setText("");
                if (totalTagCount > 0 && !operation.equalsIgnoreCase("syncRead")) {
                    if (exception.length() > 20) {
                        totalTagCountView.setText(Html
                                .fromHtml("<br>"));
                    }
                    totalTagCountView.setText(Html
                            .fromHtml("<b>Total Tags  :</b> " + totalTagCount));
                }

                System.out.println("ysq  执行这里1:");
            } else {
                System.out.println("ysq  执行这里2:");

                searchResultCount.setText(Html.fromHtml("<b>Unique Tags :</b> "
                        + epcToReadDataMap.keySet().size()));
                totalTagCountView.setText(Html.fromHtml("<b>Total Tags  :</b> "
                        + totalTagCount));
                populateSearchResult(epcToReadDataMap);
                System.out.println("unique_tag_count :" + unique_tag_count);
                unique_tag_count.setText(Integer.toString(epcToReadDataMap
                        .keySet().size()));
                total_tag_count.setText(Integer.toString(totalTagCount));
                long elapsedTime = queryStopTime - queryStartTime;
                double timeTaken = (double) ((totalTagCount) / ((double) elapsedTime / 1000));
                DecimalFormat df = new DecimalFormat("#.##");
                time_taken.setText(df.format(timeTaken) + " sec");
            }
            progressBar.setVisibility(View.INVISIBLE);
            readButton.setClickable(true);
            if (operation.equalsIgnoreCase("AsyncRead")) {
                readButton.setText("Start Reading");
            } else if (operation.equalsIgnoreCase("SyncRead")) {
                readButton.setText("Read");
            }
            readButton.setClickable(true);
            syncReadRadioButton.setClickable(true);
            asyncReadSearchRadioButton.setClickable(true);
            connectButton.setClickable(true);
            connectButton.setEnabled(true);
            if (exceptionOccur && !exception.equalsIgnoreCase("No connected antennas found")) {
                disconnectReader();
            }
        }

        private static void disconnectReader() {
            ntReaderField.setEnabled(true);
            serialList.setEnabled(true);
            customReaderField.setEnabled(true);
            serialReaderRadioButton.setClickable(true);
            networkReaderRadioButton.setClickable(true);
            customReaderRadioButton.setClickable(true);
            connectButton.setText("Connect");
            servicelayout.setVisibility(View.GONE);
            readerRadioGroup.setVisibility(View.VISIBLE);
            mReaderActivity.reader = null;
            if (!exceptionOccur) {
                searchResultCount.setText("");
                total_tag_count.setText("");
            }
        }

        private static void populateSearchResult(
                ConcurrentHashMap<String, TagRecord> epcToReadDataMap) {
            try {
                Set<String> keySet = epcToReadDataMap.keySet();
                for (String epcString : keySet) {
                    TagRecord tagRecordData = epcToReadDataMap.get(epcString);

                    if (!addedEPCRecords.contains(epcString.toString())) {

                        addedEPCRecords.add(epcString.toString());
                        uniqueRecordCount = addedEPCRecords.size();
                        if (inflater != null) {
                            //一行数据
                            fullRow = (TableRow) inflater.inflate(R.layout.row, null, true);
                            fullRow.setId(uniqueRecordCount);

                            if (fullRow != null) {
                                nr = (TextView) fullRow.findViewById(R.id.nr);
                                if (nr != null) {
                                    nr.setText(String
                                            .valueOf(uniqueRecordCount));
                                    nr.setWidth(mReaderActivity.rowNumberWidth);
                                    epcValue = (TextView) fullRow
                                            .findViewById(R.id.EPC);

                                    if (epcValue != null) {
                                        epcValue.setText(tagRecordData
                                                .getEpcString());
                                        epcValue.setMaxWidth(mReaderActivity.epcDataWidth);
                                        countView = (TextView) fullRow.findViewById(R.id.COUNT);
                                        if (countView != null) {
                                            countView.setText(String.valueOf(tagRecordData.getReadCount()));
                                            countView.setWidth(mReaderActivity.epcCountWidth);
                                        }


                                        if (isEmbeddedRead) {
                                            dataView = (TextView) fullRow.findViewById(R.id.DATA);
                                            if (dataView != null) {
                                                dataView.setVisibility(View.VISIBLE);
                                                dataView.setText(String
                                                        .valueOf(tagRecordData.getData()));
                                            }

                                        }
                                        table.addView(fullRow);
                                    }
                                }
                            }
                        }
                    } else {
                        fullRow = (TableRow) table.getChildAt(Integer
                                .valueOf(addedEPCRecords.indexOf(epcString)));
                        if (fullRow != null) {
                            countView = (TextView) fullRow.getChildAt(3);
                            if (countView != null
                                    && Integer.valueOf(countView.getText()
                                    .toString()) != tagRecordData
                                    .getReadCount()) {
                                countView.setText(String.valueOf(tagRecordData
                                        .getReadCount()));
                            }
                        }
                        LoggerUtil.error("ysq 读取到的epc", tagRecordData.getEpcString() + "次数" + tagRecordData.getReadCount());
                    }
                }
            } catch (Exception ex) {
                LoggerUtil.error(TAG, "Exception while populating tags :", ex);
            }
        }

        public static boolean isReading() {
            return reading;
        }

        public void setReading(boolean reading) {
            ReadThread.reading = reading;
        }
    }
}
