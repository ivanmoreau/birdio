package com.ivmoreau.skunkquill

import io.getquill.SnakeCase

// Test table
case class Post(username: String, text: String)

class MySuite extends munit.FunSuite {

  // Quill
  val ctx = new SkunkContext[SnakeCase]
  import ctx.*

  // SchemaMeta
  test("test1") {
    ctx.run {
      ctx.quote {
        ctx.query[Post]
      }
    }
  }
}
