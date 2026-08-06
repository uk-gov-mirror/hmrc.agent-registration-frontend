/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentregistrationfrontend.util

import uk.gov.hmrc.agentregistration.shared.InternalUserId
import uk.gov.hmrc.agentregistration.shared.Nino
import uk.gov.hmrc.agentregistration.shared.SaUtr
import scala.compiletime.testing.*
import uk.gov.hmrc.agentregistrationfrontend.testsupport.UnitSpec
import uk.gov.hmrc.agentregistrationfrontend.util.UniqueTuple.*

class UniqueTupleSpec
extends UnitSpec:

  trait Animal
  case class Fish(name: String)
  extends Animal
  case class Chicken(name: String)
  extends Animal
  case class Fox(name: String)
  extends Animal
  case class Frog(name: String)
  extends Animal
  type CanSwim = Animal & (Fish | Frog)
  type CanJump = Animal & (Fox | Frog)
  val kermit: Frog = Frog("Kermit")

  val t: UniqueTuple[(Animal, Int, Boolean)] = (kermit: Animal, 1, true).unique

  val t2 = t.replace[Animal, CanSwim & CanJump](kermit: CanSwim & CanJump)
  val t3 = t.delete[Animal].add[CanSwim & CanJump](kermit: CanSwim & CanJump)

  //  "UniqueTuple" in:
//    val t: UniqueTuple[(Int, Double)] = UniqueTuple((1, 2.0))
//    t.add(1)
//    UniqueTuple((1, 2.0)).add(123)

  "PresentIn" in:
    summon[String PresentIn Tuple1[String]]
    summon[String PresentIn (String, Int)]
    summon[String PresentIn (String, Int)]
//    summon[String PresentIn (Boolean, Int)]
//    val t = UniqueTuple((1, "string", true))
//    t.get[Double]

  "compilation safety" should:
    "fail to compile get when used with generic types" in:
      val errors = typeCheckErrors("""
        import uk.gov.hmrc.agentregistrationfrontend.util.UniqueTuple
        import uk.gov.hmrc.agentregistrationfrontend.util.UniqueTuple.*
        def f[Data <: Tuple](data: UniqueTuple[Data])(using Boolean PresentIn Data) = data.get[Boolean]
      """)
      errors.map(_.message) shouldBe List(
        "Cannot extract value from generic tuple. The method calling this must be 'inline' to preserve the tuple structure, or the value must be passed explicitly."
      )

    "fail to compile update when used with generic types" in:
      val errors = typeCheckErrors("""
        import uk.gov.hmrc.agentregistrationfrontend.util.UniqueTuple
        import uk.gov.hmrc.agentregistrationfrontend.util.UniqueTuple.*
        def f[Data <: Tuple](data: UniqueTuple[Data])(using Boolean PresentIn Data) = data.update(true)
      """)
      errors.map(_.message) shouldBe List("Cannot update value in generic tuple. The method calling this must be 'inline' to preserve the tuple structure.")

    "fail to compile replace when used with generic types" in:
      val errors = typeCheckErrors("""
        import uk.gov.hmrc.agentregistrationfrontend.util.UniqueTuple
        import uk.gov.hmrc.agentregistrationfrontend.util.UniqueTuple.*
        def f[Data <: Tuple](data: UniqueTuple[Data])(using Boolean PresentIn Data, Int AbsentIn Data) = data.replace[Boolean, Int](1)
      """)
      errors.map(_.message) shouldBe List("Cannot replace value in generic tuple. The method calling this must be 'inline' to preserve the tuple structure.")

    "fail to compile delete when used with generic types" in:
      val errors = typeCheckErrors("""
        import uk.gov.hmrc.agentregistrationfrontend.util.UniqueTuple
        import uk.gov.hmrc.agentregistrationfrontend.util.UniqueTuple.*
        def f[Data <: Tuple](data: UniqueTuple[Data])(using Boolean PresentIn Data) = data.delete[Boolean]
      """)
      errors.map(_.message) shouldBe List("Cannot delete value from generic tuple. The method calling this must be 'inline' to preserve the tuple structure.")

  "showcase" in:

//    f((1, "string").unique)
//    f((1, "string", false).unique)

    val ut: UniqueTuple[(
      Int,
      Double,
      String
    )] = (1, 2.0, "3").unique

    val ut2: UniqueTuple[(Option[String], Int, Double, String)] = ut.add(Some("x"))
    val ut3: UniqueTuple[(Option[String], Double, String)] = ut2.delete[Int]

    val x: UniqueTuple[(Animal, Option[String], Double, String)] = ut3.add(kermit)
    println(x)
    //    EmptyTuple.get[Boolean]
    val t = UniqueTuple((1, "string", true, kermit))
    t.get[Int] shouldBe 1
    t.get[String] shouldBe "string"
    t.get[Boolean] shouldBe true
    //    t.get[Any]
    //    t.get[Animal] shouldBe Frog("kermit")
    t.get[Frog] shouldBe kermit
    // UniqueTuple((1, 2)) // Should not compile because duplicates

  "UniqueTuple" should:

    "construct" in:
      val maybeNino: Option[Nino] = Some(Nino("AA123456A"))
      val maybeSaUtr: Option[SaUtr] = Some(SaUtr("1234567890"))
      val internalUserId = InternalUserId("123")

      val tuple: (Option[Nino], Option[SaUtr], InternalUserId) = (maybeNino, maybeSaUtr, internalUserId)
      val data: UniqueTuple[(Option[Nino], Option[SaUtr], InternalUserId)] = tuple.unique
      data shouldBe tuple

      val tuple2: (Option[Nino], Option[SaUtr], InternalUserId) = (maybeNino, None, internalUserId)
      val data2: UniqueTuple[(Option[Nino], Option[SaUtr], InternalUserId)] = tuple2.unique
      data2 shouldBe tuple2

    "add" should:
      "add a new element to the tuple" in:
        val t = UniqueTuple((1, "string"))
        val result = t.add(true)
        result.toTuple shouldBe (true, 1, "string")

      "fail to compile when adding a duplicate type" in:
        val errors = typeCheckErrors("""
          import uk.gov.hmrc.agentregistrationfrontend.util.UniqueTuple.*
          val t = UniqueTuple((1, "string"))
          t.add("duplicate")
        """)
        errors.map(_.message) shouldBe List(
          """Type 'String' is already present in the tuple.
            |Available types:
            |  * Int
            |  * String""".stripMargin
        )

    "get" should:
      "retrieve an existing element" in:
        val t = UniqueTuple((1, "string", true))
        t.get[Int] shouldBe 1
        t.get[String] shouldBe "string"
        t.get[Boolean] shouldBe true

      "fail to compile when type is missing" in:
        val errors = typeCheckErrors("""
          import uk.gov.hmrc.agentregistrationfrontend.util.UniqueTuple.*
          val t = UniqueTuple((1, "string", true))
          t.get[Double]
        """)
        errors.map(_.message) shouldBe List(
          "Cannot extract value from generic tuple. The method calling this must be 'inline' to preserve the tuple structure, or the value must be passed explicitly.",
          """Type 'Double' is not present in the tuple.
            |Available types:
            |  * Int
            |  * String
            |  * Boolean""".stripMargin
        )

    "update" should:
      "update an existing element" in:
        val t = UniqueTuple((1, "string", true))
        val result = t.update("new string")
        result.toTuple shouldBe (1, "new string", true)

      "fail to compile when type is missing" in:
        val errors = typeCheckErrors("""
          import uk.gov.hmrc.agentregistrationfrontend.util.UniqueTuple.*
          val t = UniqueTuple((1, "string", true))
          t.update(2.0)
        """)
        errors.map(_.message) shouldBe List(
          "Cannot update value in generic tuple. The method calling this must be 'inline' to preserve the tuple structure.",
          """Type 'Double' is not present in the tuple.
            |Available types:
            |  * Int
            |  * String
            |  * Boolean""".stripMargin
        )

    "replace" should:
      "replace an existing type with a new type" in:
        val t = UniqueTuple((1, "string", true))
        val result = t.replace[String, Double](2.0)
        result.toTuple shouldBe (1, 2.0, true)

      "fail to compile when old type is missing" in:
        val errors = typeCheckErrors("""
          import uk.gov.hmrc.agentregistrationfrontend.util.UniqueTuple.*
          val t = UniqueTuple((1, "string", true))
          t.replace[Double, Char]('c')
        """)
        errors.map(_.message) shouldBe List(
          "Cannot replace value in generic tuple. The method calling this must be 'inline' to preserve the tuple structure.",
          """Type 'Double' is not present in the tuple.
            |Available types:
            |  * Int
            |  * String
            |  * Boolean""".stripMargin
        )

    "delete" should:
      "remove an existing element" in:
        val t = UniqueTuple((1, "string", true))
        val result = t.delete[String]
        result.toTuple shouldBe (1, true)

      "fail to compile when type is missing" in:
        val errors = typeCheckErrors("""
          import uk.gov.hmrc.agentregistrationfrontend.util.UniqueTuple.*
          val t = UniqueTuple((1, "string", true))
          t.delete[Double]
        """)
        errors.map(_.message) shouldBe List(
          "Cannot delete value from generic tuple. The method calling this must be 'inline' to preserve the tuple structure.",
          """Type 'Double' is not present in the tuple.
            |Available types:
            |  * Int
            |  * String
            |  * Boolean""".stripMargin
        )

    "unique" should:
      "create a UniqueTuple from a tuple" in:
        val t = (1, "string").unique
        t.toTuple shouldBe (1, "string")

      "fail to compile for duplicates" in:
        val errors = typeCheckErrors("""
          import uk.gov.hmrc.agentregistrationfrontend.util.UniqueTuple.*
          (1, 1).unique
        """)
        errors.map(_.message) shouldBe List(
          """Tuple isn't unique. Type 'Int' occurs more than once:
            |  * Int
            |  * Int""".stripMargin
        )

    "ensureUnique" should:
      "pass for a tuple with unique types" in:
        val t = UniqueTuple((1, "string", true))
        t.toTuple shouldBe (1, "string", true)

      "fail to compile for a tuple with duplicate types" in:
        val errors = typeCheckErrors("""
          import uk.gov.hmrc.agentregistrationfrontend.util.UniqueTuple.*
          val t = UniqueTuple((1, "string", 1))
        """)
        errors.map(_.message) shouldBe List(
          """Tuple isn't unique. Type 'Int' occurs more than once:
            |  * Int
            |  * String
            |  * Int""".stripMargin
        )
