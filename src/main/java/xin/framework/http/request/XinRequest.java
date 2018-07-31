package xin.framework.http.request;

import android.text.TextUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.trello.rxlifecycle2.LifecycleTransformer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.ObservableTransformer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import xin.framework.http.api.ApiService;
import xin.framework.http.cache.CacheManager;
import xin.framework.http.callback.XinReqCallback;
import xin.framework.http.callback.XinRequestObserver;
import xin.framework.http.func.OutputFunction;
import xin.framework.http.func.ResultFunction;
import xin.framework.http.func.RetryFunction;
import xin.framework.http.helper.HttpHelper;
import xin.framework.http.helper.MediaTypes;
import xin.framework.http.output.BaseOutPut;

/**
 * 维护一些数据
 * 作者：xin on 2018/7/27 18:29
 * <p>
 * 邮箱：ittfxin@126.com
 */
public class XinRequest<T> {
    // url
    public String baseUrl;
    public String cachekey;
    // callback
    public XinReqCallback<T> reqCallback;
    public Class rspClazz;

    public Observable<BaseOutPut<T>> reqObservable;

    public int maxRetryCount;
    public int retryDelayMillis;

    private XinRequest() {
    }

    public static class Builder {

        Map<String, Object> mFieldMap;
        Map<String, String> mQueryMap;
        XinReqCallback mReqCallback;
        LifecycleTransformer<ResponseBody> mLifecycleTransformer;
        String mBaseUrl;
        String mSuffixUrl;
        String mCacheKey;
        String mPostContent;
        MediaType mMediaType;
        Class mRspClazz;
        Map<String, String> mHeaders=new HashMap<>();

        public int mRetryCount = 0;
        public int mRetryDelayMillis = 1000;

        public Builder setBaseUrl(String mBaseUrl) {
            this.mBaseUrl = mBaseUrl;

            return this;
        }

        public Builder setSuffixUrl(String mSuffixUrl) {
            this.mSuffixUrl = mSuffixUrl;
            return this;
        }


        public Builder setPostContent(String postContent, MediaType mediaType) {
            this.mPostContent = postContent;
            this.mMediaType = mediaType;
            return this;
        }

        public Builder setPostStringContent(String postContent) {
            this.mPostContent = postContent;
            this.mMediaType = MediaTypes.TEXT_PLAIN_TYPE;
            return this;
        }


        public Builder setPostJsonContent(String postContent) {
            this.mPostContent = postContent;
            this.mMediaType = MediaTypes.APPLICATION_JSON_TYPE;
            return this;
        }

        public Builder setPostJson(JsonObject json) {
            this.mPostContent = json.toString();
            this.mMediaType = MediaTypes.APPLICATION_JSON_TYPE;
            return this;
        }

        public Builder setPostJson(JsonArray jsonArray) {
            this.mPostContent = jsonArray.toString();
            this.mMediaType = MediaTypes.APPLICATION_JSON_TYPE;
            return this;
        }

        public Builder setRetryCount(int count) {
            this.mRetryCount = count;
            return this;
        }

        public Builder setRetryDelay(int millis) {
            this.mRetryDelayMillis = millis;
            return this;
        }

        public Builder setCacheKey(String cacheKey) {
            mCacheKey = cacheKey;
            return this;
        }


        public Builder addQueryParam(String key, String value) {
            if (mQueryMap == null) {
                mQueryMap = new LinkedHashMap<>();
            }
            mQueryMap.put(key, value);
            return this;
        }


        public Builder setFieldMap(Map<String, Object> map) {
            mFieldMap = map;
            return this;
        }


        public Builder setListener(Class clazz, XinReqCallback xinReqCallback) {
            mReqCallback = xinReqCallback;
            mRspClazz = clazz;
            return this;
        }


        public Builder setLifecycleTransformer(LifecycleTransformer<ResponseBody> transformer) {
            this.mLifecycleTransformer = transformer;
            return this;
        }


        public Builder setHeaders(Map<String, String> headers) {
            this.mHeaders = headers;
            return this;
        }


        public XinRequest build() {
            XinRequest xinRequest = new XinRequest();
            if (TextUtils.isEmpty(mBaseUrl)) {
                throw new NullPointerException("  request need a baseUrl");
            }
            ApiService apiService = HttpHelper.getInstance().getRetrofit(mBaseUrl).create(ApiService.class);

            // url
            xinRequest.baseUrl = mBaseUrl;
            String suffixUrl = TextUtils.isEmpty(mSuffixUrl) ? "" : mSuffixUrl;

            // callback
            xinRequest.reqCallback = mReqCallback;
            xinRequest.rspClazz = mRspClazz;

            xinRequest.cachekey = mCacheKey;
            xinRequest.retryDelayMillis = mRetryDelayMillis;
            xinRequest.maxRetryCount = mRetryCount;


            Observable<ResponseBody> reqObservable;
            if (mQueryMap != null) {
                reqObservable = apiService.get(suffixUrl, mQueryMap);
            } else if (!TextUtils.isEmpty(mPostContent) && mMediaType != null) {
                reqObservable = apiService.post(suffixUrl, RequestBody.create(mMediaType, mPostContent), mHeaders);
            } else if (mFieldMap != null) {
                reqObservable = apiService.postForm(suffixUrl, mFieldMap, mHeaders);
            } else {
                reqObservable = apiService.get(suffixUrl, mHeaders);
            }
            if (mLifecycleTransformer != null) {
                xinRequest.reqObservable = reqObservable.compose(mLifecycleTransformer).map(new ResultFunction<>(mRspClazz));
            } else {
                xinRequest.reqObservable = reqObservable.map(new ResultFunction<>(mRspClazz));
            }

            return xinRequest;

        }


    }


    public ObservableTransformer<BaseOutPut<T>, T> apiTransformerMap() {
        return new ObservableTransformer<BaseOutPut<T>, T>() {
            @Override
            public ObservableSource<T> apply(Observable<BaseOutPut<T>> apiResultObservable) {
                return apiResultObservable
                        .subscribeOn(Schedulers.io())
                        .unsubscribeOn(Schedulers.io())
                        .map(new OutputFunction<T>())
                        .observeOn(AndroidSchedulers.mainThread())
                        .retryWhen(new RetryFunction(maxRetryCount, retryDelayMillis));
            }
        };
    }


    public ObservableTransformer<BaseOutPut<T>, BaseOutPut<T>> apiTransformer() {
        return new ObservableTransformer<BaseOutPut<T>, BaseOutPut<T>>() {
            @Override
            public ObservableSource<BaseOutPut<T>> apply(Observable<BaseOutPut<T>> apiResultObservable) {
                return apiResultObservable
                        .subscribeOn(Schedulers.io())
                        .unsubscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .retryWhen(new RetryFunction(maxRetryCount, retryDelayMillis));
            }
        };
    }



    @SuppressWarnings("unchecked")
    public void OK() {
        XinRequestObserver<T> observer = new XinRequestObserver<>( reqCallback);

        if (TextUtils.isEmpty( cachekey)) {
             reqObservable.compose( apiTransformerMap()).subscribe(observer);
        } else {
            CacheManager.getInstance().load(this).subscribe(observer);
        }

    }
}
