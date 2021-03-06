package com.labor;

import com.csvreader.CsvReader;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by wyp on 15/8/12.
 */
public class Utils {


    private static Map<String, String> httpConfig;

    static {
        httpConfig = new HashMap<String, String>();
        httpConfig.put("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64; Trident/7.0; rv:11.0) like Gecko");
        httpConfig.put("Connection", "Keep-Alive");
        httpConfig.put("Cache-Control", "no-cache");
        httpConfig.put("Accept-Language", "en-US,en;q=0.8,zh-Hans-CN;q=0.5,zh-Hans;q=0.3");
        httpConfig.put("Accept-Encoding", "gzip, deflate");
        httpConfig.put("Accept", "text/html, application/xhtml+xml, */*j");
        httpConfig.put("Content_Type", "application/x-www-form-urlencoded");
    }


    private static Logger logger = Logger.getLogger(Utils.class);


    public static void threadSleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            logger.error(e.getMessage());
        }
    }


    public static Date strToDate(String time) throws ParseException {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd-HH:mm:ss");
        return formatter.parse(time);
    }


    public static String dateToStr(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return formatter.format(date);
    }


    public static CloseableHttpResponse postUtilNoDbFailure(CloseableHttpClient httpclient, String requestUrl,
                                                            Map<String, String> params, int maxRetry,
                                                            String mailUser, int timeOut) {
        CloseableHttpResponse response = null;
        try {
            for (int i = 0; i < maxRetry; i++) {
                response = postUtilOK(httpclient, requestUrl, params, maxRetry, timeOut);
                if (response == null) {
                    threadSleep(500);
                    continue;
                }
                String content = EntityUtils.toString(response.getEntity());
                if (Constants.PARRTERN_BEFORE_START.matcher(content).find()) {
                    logger.error(String.format(Constants.PARRTERN_BEFORE_START.toString() + "[%s]", mailUser));
                    threadSleep(500);
                    continue;
                } else if (Constants.PARRTERN_DB_FAIL.matcher(content).find()) {
                    logger.error(String.format(Constants.PARRTERN_DB_FAIL.toString() + "[%s]", mailUser));
                    threadSleep(500);
                    continue;
                } else if (Constants.PARRTERN_REG_ERROR.matcher(content).find()) {
                    logger.error(String.format(Constants.PARRTERN_REG_ERROR.toString() + "[%s]", mailUser));
                    threadSleep(500);
                    continue;
                } else if (Constants.PARRTERN_UNKNOWN_ERR.matcher(content).find()) {
                    logger.error(String.format(Constants.PARRTERN_UNKNOWN_ERR.toString() + "[%s]", mailUser));
                    threadSleep(500);
                    continue;
                } else if (Constants.PARRTERN_OUT_ERROR.matcher(content).find()) {
                    logger.error(String.format(Constants.PARRTERN_OUT_ERROR.toString() + "[%s]", mailUser));
                    threadSleep(500);
                    continue;
                } else {
                    return response;
                }
            }
        } catch (IOException e) {
            logger.error("postutilNoDbFailure " + e.getMessage());
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }


    public static CloseableHttpResponse postUtilOK(CloseableHttpClient httpclient, String requestUrl, Map<String, String> params,
                                                    int maxRetry, int timeOut) {
        HttpPost post = new HttpPost(requestUrl);
        List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        CloseableHttpResponse response = null;
        for (String key : params.keySet()) {
            nvps.add(new BasicNameValuePair(key, params.get(key)));
        }
        if (timeOut > 0) {
            RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(timeOut).setConnectTimeout(timeOut).build();//设置请求和传输超时时间
            post.setConfig(requestConfig);
        }
        for (String key : httpConfig.keySet()) {
            post.addHeader(key, httpConfig.get(key));
        }
        int status;
        for (int i = 0; i < maxRetry; i++) {
            try {
                post.setEntity(new UrlEncodedFormEntity(nvps));
                response = httpclient.execute(post);
                status = response.getStatusLine().getStatusCode();
                if (status == 200) {
                    return response;
                }
            } catch (UnsupportedEncodingException e) {
                logger.error(e.getMessage());
            } catch (ClientProtocolException e) {
                logger.error(e.getMessage());
            } catch (IOException e) {
                logger.error("postUtilOK " + e.getMessage());
            }
        }
        return response;
    }


    public static CloseableHttpResponse getUtilOK(CloseableHttpClient httpclient, String requestUrl, Map<String, String> params,
                                                   int maxRetry, int timeOut) {
        CloseableHttpResponse response = null;
        for (int i = 0; i < maxRetry; i++) {
            try {
                if (params != null && params.size() > 0) {
                    List<NameValuePair> pairs = new ArrayList<NameValuePair>(params.size());
                    for (String key : params.keySet()) {
                        pairs.add(new BasicNameValuePair(key, params.get(key)));
                    }
                    requestUrl += "?" + EntityUtils.toString(new UrlEncodedFormEntity(pairs, "utf-8"));
                }
                HttpGet httpGet = new HttpGet(requestUrl);
                if (timeOut > 0) {
                    RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(timeOut).setConnectTimeout(timeOut).build();//设置请求和传输超时时间
                    httpGet.setConfig(requestConfig);
                }
                for (String key : httpConfig.keySet()) {
                    httpGet.addHeader(key, httpConfig.get(key));
                }
                response = httpclient.execute(httpGet);
            } catch (IOException e) {
                logger.error("getUtilOK " + e.getMessage());
            }
            if (response == null) {
                continue;
            } else if (response.getStatusLine().getStatusCode() == 200) {
                return response;
            }
        }
        return response;
    }


    public static CloseableHttpResponse getUtilNoErr(CloseableHttpClient httpclient, String requestUrl, Map<String, String> params,
                                                     int maxRetry, String mailUser, int timeOut) {
        CloseableHttpResponse response = null;
        try {
            for (int i = 0; i < maxRetry; i++) {
                response = getUtilOK(httpclient, requestUrl, params, maxRetry, timeOut);
                if (response == null) {
                    threadSleep(500);
                    continue;
                }
                String content = EntityUtils.toString(response.getEntity());
                if (Constants.PARRTERN_BEFORE_START.matcher(content).find()) {
                    logger.error(String.format(Constants.PARRTERN_BEFORE_START.toString() + "[%s]", mailUser));
                    threadSleep(500);
                    continue;
                } else if (Constants.PARRTERN_SESSION_ERR.matcher(content).find()) {
                    logger.error(String.format(Constants.PARRTERN_SESSION_ERR.toString() + "[%s]", mailUser));
                    threadSleep(500);
                    continue;
                } else if (Constants.PARRTERN_ZSCALER.matcher(content).find()) {
                    logger.error(String.format(Constants.PARRTERN_ZSCALER.toString() + "[%s]", mailUser));
                    threadSleep(500);
                    continue;
                } else if (Constants.PARRTERN_REG_ERROR.matcher(content).find()) {
                    logger.error(String.format(Constants.PARRTERN_REG_ERROR.toString() + "[%s]", mailUser));
                    threadSleep(500);
                    continue;
                } else {
                    return response;
                }
            }
        } catch (IOException e) {
            logger.error("getUtilNoErr " + e.getMessage());
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return response;
    }


    public static String processResponse(CloseableHttpResponse response) {
        try {
            String content = EntityUtils.toString(response.getEntity());
            response.close();
            return content;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    public static String encodeJP(String str) {
        try {
            return URLEncoder.encode(str, "shift_jis");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }


    public static void outputFile(String content, String fileName) {
        FileOutputStream fileOutputStream = null;
        try {
            File file = new File(fileName);
            fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(content.getBytes());
        } catch (IOException e) {
            logger.error(e.getMessage());
        } finally {
            try {
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static List<Map<String, String>> readCSVFile(File csvFile) throws IOException {
        List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        CsvReader reader = new CsvReader(new FileReader(csvFile), ',');
        reader.readHeaders();
        String[] headers = reader.getHeaders();
        while (reader.readRecord()) {
            Map<String, String> infoMap = new HashMap<String, String>();
            for (int i = 0; i < headers.length; i++) {
                String value = reader.get(headers[i]);
                infoMap.put(headers[i], value);
            }
            list.add(infoMap);
        }
        return list;
    }

    public static void writeCsv(String[] content, FileOutputStream fileOutputStream) throws IOException {
        for (int i = 0; i < content.length; i++) {
            String s = content[i];
            if (i != content.length - 1) {
                s += ",";
            } else {
                s += "\n";
            }
            fileOutputStream.write(s.getBytes());
        }
    }

}
