package com.multiappcloner.pro;

/** A clone profile. A real, installed clone exists only while status is "active" and clonePackage is installed. */
public class CloneProfile {
    public long id;
    public String appName;
    public String packageName;
    public String cloneName;
    public String method;
    public long networkProfileId = -1;
    public long createdAt;
    public String status;
    public String note = "";
    public String clonePackage = "";
}
