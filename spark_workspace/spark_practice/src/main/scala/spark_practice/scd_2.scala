
package spark_practice
import org.apache.spark.sql.functions._
import java.sql.Date

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.StringType


object scd_2 {
  case class CustomerUpdate(customerId: Int,
                            address: String,
                            effectiveStartDate: Date)

  case class Customer(customerId: Int,
                      address: String,
                      current: Boolean,
                      effectiveStartDate: Date,
                      effectiveEndDate: Date)

  implicit def date(str: String): java.sql.Date = java.sql.Date.valueOf(str)
  def main(args: Array[String]): Unit = {

    System.setProperty("hadoop.home.dir","C:\\Spark_3_Software");
    val spark = SparkSession.builder().master("local[3]").appName("SCD2_Impl")
      .config("spark.sql.shuffle.partitions",3).getOrCreate()

    import spark.implicits._
    val currentDS = Seq(
      Customer(1, "old address for 1", false, null, "2018-02-01"),
      Customer(1, "current address for 1", true, "2018-02-01", null),
      Customer(2, "current address for 2", true, "2018-02-01", null),
      Customer(3, "current address for 3", true, "2018-04-04", null)
    ).toDS()
    currentDS.selectExpr("sum(customerId) as sum_customerID").show(false)

    val newDS = Seq(
      CustomerUpdate(1, "new address for 1", "2018-03-03"),
      CustomerUpdate(3, "current address for 3", "2018-04-04"),    // new address same as current address for customer 3
      CustomerUpdate(4, "new address for 4", "2018-04-04")
    ).toDS()
   // newDS.createOrReplaceTempView("new_customers")
    newDS.show(false)

    currentDS.printSchema()
    newDS.printSchema()
    println("common data set :: Inner Join ")
    val joinExp  = col("left.customerId").equalTo(col("right.customerId"))
                  .and( col("left.address").equalTo(col("right.address")) )
    val INNER : String = "inner"
    val commonDS = newDS.as("left").join(currentDS.as("right"),joinExp,INNER )
                                           .select("right.*").show(false)
    // just lookup
    println("retained ds =============================")
    val retainedDS = currentDS.join(newDS,Seq("customerId","address"),"left_semi")
                              .withColumn("flag",lit("R"))
    retainedDS.show(false)

    println("delete ds =============================")

    val retainedDS2 = currentDS.as("lt").join(
      newDS.as("rt"),
      col("lt.customerId").equalTo( col("rt.customerId") )
                          .and(
                            col("lt.address").cast(StringType).notEqual(col("rt.address").cast(StringType)
                          )
                   ),"inner"
    ).select("lt.*")
    .withColumn("effectiveEndDate",
        when(col("effectiveEndDate").isNull,lit(current_date())).otherwise(col("effectiveEndDate"))
      )
    .withColumn("flag",lit("R"))
        .withColumn("flag",when(col("current").equalTo(lit("true")),lit("U")).otherwise(col("flag"))


        ).withColumn("current",
      when(col("flag").equalTo(lit("U")),lit(false)).otherwise(col("current"))


    )

    retainedDS2.show(false)

    println("deletedDS ============================")
    val deletedDS = currentDS.as("lt").join(
      newDS.as("rt"),col("lt.customerId").equalTo(col("rt.customerId")),"left_anti"

    ).withColumn("current",lit(false))
      .withColumn("flag",lit("D"))
      .withColumn("effectiveEndDate",
      when(col("effectiveEndDate").isNull,lit(current_date())).otherwise(col("effectiveEndDate"))
    )
    deletedDS.show()
    val insertDS = newDS.join(currentDS,Seq("customerId","address"),"left_anti")
      .withColumn("current",lit(true))
      .withColumn("effectiveEndDate",lit(null))
      .withColumn("flag",lit("I"))
    insertDS.show(false)

   val scd_final = retainedDS.unionByName(retainedDS2).unionByName(insertDS).unionByName(deletedDS)
    scd_final.show(false)


    //union then all



    
    spark.stop()

  }
}

