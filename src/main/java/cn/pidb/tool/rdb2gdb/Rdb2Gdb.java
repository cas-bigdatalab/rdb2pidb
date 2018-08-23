package cn.pidb.tool.rdb2gdb;

import cn.pidb.tool.rdb2gdb.entity.ColItem;
import cn.pidb.tool.rdb2gdb.entity.ForeignKeyInfo;
import com.alibaba.fastjson.JSON;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.neo4j.driver.v1.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class Rdb2Gdb
{
    private static Logger logger = LogManager.getLogger(Rdb2Gdb.class.getName());

    static
    {
        //BasicConfigurator.configure();
        PropertyConfigurator.configure("./conf/log4j.properties");
    }

    public static void main(String[] args)
    {
        rdb2Gdb();
    }

    /**
     * 实现了rdb到gdb得输入导入功能的工具。
     * 通过从配置文件中读取配置信息，构建select语句，对查出来的每一个记录，在pidb中建立一个顶点
     */
    public static void rdb2Gdb()
    {
        //获取rdbms的链接和图数据库中的事务
        Connection connection = getRdbConnection();
        Transaction transaction = getGdbTransaction();

        //要导入数据的表名
        String tableName = ConfigModel.getConfigJsonObject().getString("tableName");
        logger.info("table: '" + tableName + "' will be imported into graphdb.");

        //获取表的外键信息
        List<ForeignKeyInfo> foreignKeyInfoList = new ArrayList<ForeignKeyInfo>();
        Object[] foreignKeyInfos = ConfigModel.getConfigJsonObject().getJSONArray("foreignKeys").toArray();
        for (Object foreignKeyInfo: foreignKeyInfos)
        {
            foreignKeyInfoList.add(JSON.parseObject(foreignKeyInfo.toString(), ForeignKeyInfo.class));
        }
        logger.info("foreign key info of table '" + tableName + "' : foreignKeyInfoList : " + foreignKeyInfoList.toString());

        //获取数据表的列信息
        List<ColItem> colItemList = new ArrayList<ColItem>();
        Object[] colItems = ConfigModel.getConfigJsonObject().getJSONArray("cols").toArray();
        for (Object col: colItems)
        {
            colItemList.add(JSON.parseObject(col.toString(), ColItem.class));
        }
        int numOfCols = colItemList.size();
        logger.info("column info of table '" + tableName + "' colItemList : " + colItemList);

        // 构造从关系数据库查询数据的语句
        String queryStmt = "";
        String colStr = "";  //
        for (int i = 0; i < numOfCols; i++)
        {
            colStr = (colStr.equals("") ? colStr : colStr + ", ") + colItemList.get(i).getColName();
        }
        queryStmt = "select " + colStr  + " from " + tableName;
        logger.info("queryStatement:  " + queryStmt);

        try
        {
            //查询关系数据库，并且将符合条件的记录插入到图数据库中
            Statement statement = null;
            ResultSet resultSet = null;
            statement = connection.createStatement();
            resultSet = statement.executeQuery(queryStmt);
            logger.info("query records from rdb completed, then insert them into graphdb.");

            //把查询出来的记录插入到图数据库中
            while (resultSet.next())
            {
                logger.info("insert records into graph db, one record one vertex.");

                //构造将记录插入图数据库中去的cypher语句
                logger.info("construct the cypher create statement for record insertion.");
                String createCypherStatement = "create";
                String propertyInfo = "(n: " + tableName + "{";
                for (int i = 0; i < numOfCols; i++)
                {
                    String colName = colItemList.get(i).getColName();
                    String colType = colItemList.get(i).getColType();

                    //每一个列的值需要单独处理，这里还有一点问题
                    String colValue = resultSet.getString(i + 1);
                    //给String类型和Datetime类型加上引号，其他的Number，Boolean， Blob类型按原样
                    //Blob类型需要单独处理
                    /*if (colType.trim().equalsIgnoreCase("String") || colType.trim().equalsIgnoreCase("Datetime") || colType.trim().equalsIgnoreCase("Blob"))
                    {
                        colValue = "'" + colValue + "'";
                    }*/

                    // (n: tableName {id: 1, name: "James"})
                    propertyInfo = (i == 0 ? propertyInfo : propertyInfo + ", ") + colName + ": '" + colValue + "'";
                }
                propertyInfo = propertyInfo + "})";
                createCypherStatement = createCypherStatement + propertyInfo;
                logger.info("cypher create statement construction finished. createCypherStatement : " + createCypherStatement);

                /**
                 * 检查是否已经存在了
                 *
                 * 因为外键的存在，某个表的主键有可能是其他表的外键，而导入有外键存在的表的时候，会一并导入被参考的表的数据，
                 * 因此就有可能会存在这个表的数据已经被导入了的情况
                 * 所以在导入每一个记录之前都要检查此记录是否被导入过了。
                 */
                logger.info("check if the record to be inserted into graphdb has already existed.");
                String matchCypherStatement = "match " + propertyInfo + " return n";
                logger.info("cypher statement for checking, matchCypherStatement = " + matchCypherStatement);
                StatementResult stmtResult = transaction.run(matchCypherStatement);
                transaction.success();
                //如果已经插入过了，那么就不需要再次插入
                if (stmtResult.list().size() != 0)
                {
                    logger.info("vertext has been inserted already, insertion aborted.");
                    continue;
                }
                logger.info("the record is not in graphdb, so insertion starts.");

                /*
                 * 将记录插入图数据库中
                 *
                 * 这里在插入的时候，要把外键关联的表中对应的记录一并插入进去
                 * 为了便于理解，这里需要定义两个名词：
                 * （1）主记录，就是即将要插入的关系数据中的记录
                 * （2）外记录，或者关联记录，指的是与主记录通过外键关联的关联表中的记录
                 */
                transaction.run(createCypherStatement);
                transaction.success();
                logger.info("main record inserted. createCypherStatement = " + createCypherStatement);

                /**
                 *处理与此记录相关的外键
                 */
                logger.info("the record to be inserted has related foreign record, so starts to process foreign keys");
                //如果没有外键，就不需要处理了
                if (foreignKeyInfoList.size() == 0)
                {
                    logger.info("table : '" + tableName + "' does not has foregin key info");
                    continue;
                }

                //表中的外键不止一个，会有多个
                logger.info("table '" + tableName + "' has " + foreignKeyInfoList.size() + " foreign keys.");
                for (ForeignKeyInfo foreignKeyInfo : foreignKeyInfoList)
                {
                    String foreignKey = foreignKeyInfo.getForeignKey();
                    String foreignKeyValue = resultSet.getString(foreignKey);

                    String referenceTableName = foreignKeyInfo.getReferenceTableInfo().getTableName();
                    String referenceKey = foreignKeyInfo.getReferenceKey();

                    //检查这个关联记录是否已经插入过了,如果已经插入过了，就不需要插入了，只需要把关联关系建上就好了
                    logger.info("check if the foreign record has been inserted.");
                    String cypherQueryStmt = "match (n : " + referenceTableName + "{" + referenceKey + ":'" + foreignKeyValue + "'}) return n";
                    logger.info("cypherQueryStmt = " + cypherQueryStmt);
                    StatementResult queryResult = transaction.run(cypherQueryStmt);

                    //通过检查，如果发现没有这个节点，则创建这个关联节点
                    String foreignCypherCreateStmt = "create ";
                    String foreignPropertyInfo = "(m : " + referenceTableName + "{";

                    if (queryResult.list().size() == 0)
                    {
                        //插入外键关联的表中的记录
                        logger.info("construct foreign record insertion cypher statement.");

                        String colInfo = "";
                        for (ColItem colItem : foreignKeyInfo.getReferenceTableInfo().getCols()) {
                            colInfo = colInfo + (colInfo.equals("") ? "" : ", ") + colItem.getColName();
                        }
                        String selectStmt = "select " + colInfo + " from " + referenceTableName + " where " + referenceKey + " = " + foreignKeyValue;
                        Statement statement1 = connection.createStatement();
                        ResultSet resultSet1 = statement1.executeQuery(selectStmt);
                        resultSet1.next();
                        ColItem[] referenceTableColItems = foreignKeyInfo.getReferenceTableInfo().getCols();
                        String colName, colType, colValue;
                        for (int i = 0; i < referenceTableColItems.length; i++) {
                            colName = referenceTableColItems[i].getColName();
                            colType = referenceTableColItems[i].getColType();
                            colValue = resultSet1.getString(i + 1);
                            foreignPropertyInfo = foreignPropertyInfo + (i == 0 ? "" : ", ") + colName + ": '" + colValue + "'";
                        }
                        resultSet1.close();

                        foreignPropertyInfo = foreignPropertyInfo + "})";
                        foreignCypherCreateStmt = foreignCypherCreateStmt + foreignPropertyInfo;
                        transaction.run(foreignCypherCreateStmt);
                        logger.info("foreign record has been inserted into graphdb, foreignCypherCreateStmt = " + foreignCypherCreateStmt);
                    }
                    else
                    {
                        logger.info("foreign record has been in graphdb.");
                        foreignPropertyInfo = foreignPropertyInfo + referenceKey + ": '" + foreignKeyValue + "'})";
                    }

                    //创建主节点和关联节点之间的关联关系
                    String relationCreateCypherStmt = "match " + propertyInfo + ", " + foreignPropertyInfo + " create (n)-[r: " + referenceTableName + "]->(m)";
                    transaction.run(relationCreateCypherStmt);
                    transaction.success();
                    logger.info("CypherStatement for relation : " + relationCreateCypherStmt);
                    logger.info("records and its related foreign records and relation between them has been inserted into graphdb.");
                }
            }

            resultSet.close();
            connection.close();
            transaction.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     *获取图数据库的事务
     * @return transaction 事务
     */
    public static Transaction getGdbTransaction()
    {
        String url4Graphdb = ConfigModel.getConfigJsonObject().getString("url4Gdb");
        String gUserName = ConfigModel.getConfigJsonObject().getString("gUserName");
        String gPassword = ConfigModel.getConfigJsonObject().getString("gPassword");

        logger.info("graphdb info : " + "url4Gdb: " + url4Graphdb + ", gUserName: " + gUserName + ", gPassword: " + gPassword);

        Driver driver = GraphDatabase.driver(url4Graphdb, AuthTokens.basic(gUserName, gPassword));
        Transaction transaction = null;
        try
        {
            transaction = driver.session().beginTransaction();
            logger.info("graphdb transaction got successfully.");
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return transaction;
    }

    /**
     * 取得关系数据的连接
     * @return Connection 连接
     */
    public static Connection getRdbConnection()
    {
        String url4Rdb = ConfigModel.getConfigJsonObject().getString("url4Rdb");
        String rUserName = ConfigModel.getConfigJsonObject().getString("rUserName");
        String rPassword = ConfigModel.getConfigJsonObject().getString("rPassword");
        String rdbDriverClass = ConfigModel.getConfigJsonObject().getString("rdbDriverClass");

        logger.info("rdb info : " + "url4Rdb: " + url4Rdb + ", rUserName: " + rUserName + ", rPassword: " + rPassword + "rdbDriverClass" + rdbDriverClass);

        Connection connection = null;
        try
        {
            Class.forName(rdbDriverClass);
            connection = DriverManager.getConnection(url4Rdb, rUserName, rPassword);
            logger.info("rdb connection got successfully.");
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return connection;
    }
}
