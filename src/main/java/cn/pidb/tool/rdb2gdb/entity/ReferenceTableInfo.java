package cn.pidb.tool.rdb2gdb.entity;

import java.util.Arrays;

public class ReferenceTableInfo {
    private String tableName;
    private ColItem[] cols;

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public ColItem[] getCols() {
        return cols;
    }

    public void setCols(ColItem[] cols) {
        this.cols = cols;
    }

    @Override
    public String toString() {
        return "ReferenceTableInfo{" +
                "tableName='" + tableName + '\'' +
                ", cols=" + Arrays.toString(cols) +
                '}';
    }
}
