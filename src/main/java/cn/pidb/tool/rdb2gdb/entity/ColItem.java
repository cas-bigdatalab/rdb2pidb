package cn.pidb.tool.rdb2gdb.entity;

public class ColItem
{
    private String colName;
    private String colType;

    public String getColName() {
        return colName;
    }

    public void setColName(String colName) {
        this.colName = colName;
    }

    public String getColType() {
        return colType;
    }

    public void setColType(String colType) {
        this.colType = colType;
    }

    @Override
    public String toString() {
        return "ColItem{" +
                "colName='" + colName + '\'' +
                ", colType='" + colType + '\'' +
                '}';
    }
}
