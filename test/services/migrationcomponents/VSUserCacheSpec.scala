package services.migrationcomponents

import org.specs2.mutable.Specification

class VSUserCacheSpec extends Specification {
  "VSUserCache.initialise" should {
    "load in user data from a path" in {
      val result = VSUserCache.initialize("test/testdata/fakeusers.json")
      result must beSuccessfulTry
      result.get.lookup(80) must beSome("jane_jones")
      result.get.lookup(444234) must beNone
    }

    "report failure if the file is not valid" in {
      val result = VSUserCache.initialize("i_dont_exist.json")
      result must beFailedTry
    }
  }
}
