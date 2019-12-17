package com.ilabs.dsi.modelserver.utils

import java.sql.DriverManager
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date

import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.Await
import scala.concurrent.duration.Duration


/**
  * Class to coordinate API calls with the backend.
  * Created by samik on 15/3/17.
  */
object DBManager
{
    // Set up logger
    val log = LoggerFactory.getLogger("DBManager")
    // Open the database with respecting foreign key constraints
    //private val db = Database.forConfig("ourdb")
    //val conn = DriverManager.getConnection(ConfigFactory.load().getString("ourdb.url"))

    def getUser(devKey: String) =
    {
        val db = Database.forConfig("db", ModelServerConfig.config)
        val res = Await.result(db.run(sql"""select userId from users where devKey = $devKey """.as[String]), Duration.Inf)(0)
        db.close()
        res
    }

    def checkIfUserRegistered(devKey: String) =
    {
        val db = Database.forConfig("db", ModelServerConfig.config)
        val res = Await.result(db.run(sql"""select case when exists ( select userId from users where devKey = $devKey ) then 'TRUE' else 'FALSE' end""".as[Boolean]), Duration.Inf)(0)
        db.close()
        res
    }

    def checkUserAlreadyExist(userId: String): Boolean = {
        val db = Database.forConfig("db", ModelServerConfig.config)
        val res = Await.result(db.run(sql"""select case when exists ( select userId from users where userId = $userId ) then 'TRUE' else 'FALSE' end""".as[Boolean]), Duration.Inf)(0)
        db.close()
        res
    }

    def setUser(userId: String, devKey: String, isAdmin: Boolean): Unit =
    {
        val SQLITE_DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"
        val dateStorageformat = new SimpleDateFormat(SQLITE_DATETIME_FORMAT)
        val db = Database.forConfig("db", ModelServerConfig.config)
        Await.result(db.run(sqlu"insert into users values($userId, $devKey, ${dateStorageformat.format(Date.from(Instant.now))},$isAdmin)"), Duration.Inf)
        db.close()
    }

    def checkIfModelExist(modelId:String,version:String):Boolean =
        {
            val db = Database.forConfig("db", ModelServerConfig.config)
            val res = Await.result(db.run(sql"""select case when exists ( select modelId from tucanamodels where modelId = $modelId and version = $version ) then 'TRUE' else 'FALSE' end""".as[Boolean]), Duration.Inf)(0)
            db.close()
            res
        }

    def getModels(devKey: String) =
    {
        val db = Database.forConfig("db", ModelServerConfig.config)
        val res = Await.result(db.run(
            sql"""select m.version, m.userId, m.modelId, m.description, m.lastUpdateTimestamp from tucanamodels m
                 inner join users u
                    on m.userId = u.userId
                 where u.devKey = $devKey""".as[(String, String, String, String, String)]), Duration.Inf)
        db.close()
        res
    }

    def setModel(modelId: String, version: String, userId: String, description: String, model: Array[Byte], schema: String): Unit =
    {
        val conn = DriverManager.getConnection(ModelServerConfig.get("db.url"), ModelServerConfig.get("db.user"), ModelServerConfig.get("db.password"))
        val sql = "insert into tucanamodels values(?,?,?,?,?,?,?)"
        val stmt = conn.prepareStatement(sql)
        stmt.setString(1, modelId)
        stmt.setString(2, version)
        stmt.setString(3, userId)
        stmt.setString(4, description)
        stmt.setString(5, s"${Date.from(Instant.now).toString}")
        stmt.setBytes(6, model)
        stmt.setString(7, schema)
        //stmt.setString(8, modelType)
        stmt.executeUpdate()
        stmt.close()
        conn.close()
    }

    /*def inactivateProject(devKey: String, projectId: String) =
    {
        //TODO
    }*/
}
