package com.redis

import scala.concurrent.Future

import org.scalatest.exceptions.TestFailedException
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

import serialization._


@RunWith(classOf[JUnitRunner])
class ClientSpec extends RedisSpecBase {

  import Parse.Implicits._
  describe("non blocking apis using futures") {
    it("get and set should be non blocking") {
      @volatile var callbackExecuted = false

      val ks = (1 to 10).map(i => s"client_key_$i")
      val kvs = ks.zip(1 to 10)

      val sets: Seq[Future[Boolean]] = kvs map {
        case (k, v) => client.set(k, v)
      }

      val setResult = Future.sequence(sets) map { r: Seq[Boolean] =>
        callbackExecuted = true
        r
      }

      callbackExecuted should be (false)
      setResult.futureValue should contain only (true)
      callbackExecuted should be (true)

      callbackExecuted = false
      val gets: Seq[Future[Option[Long]]] = ks.map { k => client.get[Long](k) }
      val getResult = Future.sequence(gets).map { rs =>
        callbackExecuted = true
        rs.flatten.sum
      }

      callbackExecuted should be (false)
      getResult.futureValue should equal (55)
      callbackExecuted should be (true)
    }

    it("should compose with sequential combinator") {
      val key = "client_key_seq"
      val values = (1 to 100).toList
      val pushResult = client.lpush(key, 0, values:_*)
      val getResult = client.lrange[Long](key, 0, -1)

      val res = for {
        p <- pushResult.mapTo[Long]
        if p > 0
        r <- getResult.mapTo[List[Long]]
      } yield (p, r)

      val (count, list) = res.futureValue
      count should equal (101)
      list.reverse should equal (0 to 100)
    }
  }

  describe("error handling using promise failure") {
    it("should give error trying to lpush on a key that has a non list value") {
      val key = "client_err"
      val v = client.set(key, "value200")
      v.futureValue should be (true)

      val x = client.lpush(key, 1200)
      val thrown = evaluating { x.futureValue } should produce [TestFailedException]
      thrown.getCause.getMessage should equal ("ERR Operation against a key holding the wrong kind of value")
    }
  }
}