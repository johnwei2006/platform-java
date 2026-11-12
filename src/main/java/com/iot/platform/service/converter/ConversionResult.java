package com.iot.platform.service.converter;

/**
 * 一次业务转换的结果，用于日志与测试断言。
 */
public class ConversionResult {

    public static final String STATUS_OK = "OK";
    public static final String STATUS_SKIPPED = "SKIPPED";
    public static final String STATUS_NO_CONVERTER = "NO_CONVERTER";
    public static final String STATUS_ERROR = "ERROR";

    private boolean handled;
    private int processed;
    private int skipped;
    private String status;
    private String error;

    public static ConversionResult notHandled() {
        ConversionResult r = new ConversionResult();
        r.status = STATUS_NO_CONVERTER;
        return r;
    }

    public static ConversionResult skipped(String reason) {
        ConversionResult r = new ConversionResult();
        r.status = STATUS_SKIPPED;
        r.error = reason;
        return r;
    }

    public static ConversionResult success(int processed, int skipped) {
        ConversionResult r = new ConversionResult();
        r.handled = true;
        r.processed = processed;
        r.skipped = skipped;
        r.status = STATUS_OK;
        return r;
    }

    public static ConversionResult failure(String error) {
        ConversionResult r = new ConversionResult();
        r.handled = true;
        r.status = STATUS_ERROR;
        r.error = error;
        return r;
    }

    public ConversionResult merge(ConversionResult other) {
        if (other == null) {
            return this;
        }
        ConversionResult r = new ConversionResult();
        r.handled = this.handled || other.handled;
        r.processed = this.processed + other.processed;
        r.skipped = this.skipped + other.skipped;
        r.error = this.error != null ? this.error : other.error;
        if (STATUS_ERROR.equals(this.status) || STATUS_ERROR.equals(other.status)) {
            r.status = STATUS_ERROR;
        } else if (r.handled) {
            r.status = STATUS_OK;
        } else {
            r.status = this.status;
        }
        return r;
    }

    public boolean isHandled() {
        return handled;
    }

    public void setHandled(boolean handled) {
        this.handled = handled;
    }

    public int getProcessed() {
        return processed;
    }

    public void setProcessed(int processed) {
        this.processed = processed;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    @Override
    public String toString() {
        return "ConversionResult{status=" + status + ", handled=" + handled
                + ", processed=" + processed + ", skipped=" + skipped
                + (error == null ? "" : ", error=" + error) + '}';
    }
}
