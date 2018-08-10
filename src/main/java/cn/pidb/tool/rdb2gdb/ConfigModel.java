package cn.pidb.tool.rdb2gdb;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONReader;

import java.io.File;
import java.io.FileReader;

public class ConfigModel
{
    private static JSONObject configJsonObject;

    static
    {
        try
        {
            configJsonObject = (JSONObject)(new JSONReader(new FileReader(new File("./conf/rdb2gdb.json"))).readObject());
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static JSONObject getConfigJsonObject()
    {
        return configJsonObject;
    }
}
