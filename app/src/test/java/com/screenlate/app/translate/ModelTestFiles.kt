package com.screenlate.app.translate

import org.json.JSONArray
import org.junit.Assume.assumeTrue
import java.io.File

/**
 * Tests that need the native tokenizer and the model run only where CI provides them
 * (see build.yml); elsewhere they are skipped.
 */
object ModelTestFiles {
    fun modelDir(): File {
        val path = System.getProperty("screenlate.modelDir")
        assumeTrue("screenlate.modelDir is not set", !path.isNullOrEmpty())
        return File(path!!).also { check(it.isDirectory) { "model dir $it does not exist" } }
    }

    fun reference(name: String): JSONArray {
        val stream = checkNotNull(javaClass.getResourceAsStream("/reference/$name")) { "missing reference $name" }
        return JSONArray(stream.bufferedReader(Charsets.UTF_8).readText())
    }
}
