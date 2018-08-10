package cn.pidb.tool.rdb2gdb.entity;

import java.util.Arrays;

public class ForeignKeyInfo
{
    private String foreignKey;
    private String referenceKey;
    private String foreignKeyDesc;
    private ReferenceTableInfo referenceTableInfo;

    public String getForeignKey() {
        return foreignKey;
    }

    public void setForeignKey(String foreignKey) {
        this.foreignKey = foreignKey;
    }

    public String getReferenceKey() {
        return referenceKey;
    }

    public void setReferenceKey(String referenceKey) {
        this.referenceKey = referenceKey;
    }

    public String getForeignKeyDesc() {
        return foreignKeyDesc;
    }

    public void setForeignKeyDesc(String foreignKeyDesc) {
        this.foreignKeyDesc = foreignKeyDesc;
    }

    public ReferenceTableInfo getReferenceTableInfo() {
        return referenceTableInfo;
    }

    public void setReferenceTableInfo(ReferenceTableInfo referenceTableInfo) {
        this.referenceTableInfo = referenceTableInfo;
    }

    @Override
    public String toString() {
        return "ForeignKeyInfo{" +
                "foreignKey='" + foreignKey + '\'' +
                ", referenceKey='" + referenceKey + '\'' +
                ", foreignKeyDesc='" + foreignKeyDesc + '\'' +
                ", referenceTableInfo=" + referenceTableInfo +
                '}';
    }
}
