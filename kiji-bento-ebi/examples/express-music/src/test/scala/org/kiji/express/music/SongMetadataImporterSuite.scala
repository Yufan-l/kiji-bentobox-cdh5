/**
 * (c) Copyright 2013 WibiData, Inc.
 *
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
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

package org.kiji.express.music

import scala.collection.mutable.Buffer

import com.twitter.scalding._
import org.apache.avro.generic.GenericRecord

import org.kiji.express._
import org.kiji.express.flow._
import org.kiji.express.flow.util.ResourceUtil
import org.kiji.schema.EntityId

/**
 * A test that ensures the Song Metadata importer can import song data into a Kiji table.
 */
class SongMetadataImporterSuite extends KijiSuite {
  // Get a Kiji to use for the test and record the Kiji URI of the songs table we'll test against.
  val kiji = makeTestKiji("SongMetadataImporterSuite")
  val tableURI = kiji.getURI().toString + "/songs"

  // Execute the DDL shell commands in music-schema.ddl to create the tables for the music
  // tutorial, including the songs table.
  ResourceUtil.executeDDLResource(kiji, "org/kiji/express/music/music-schema.ddl")

  // Create a fake record to import, as a tuple. The first tuple element (0) is a dummy file
  // offset, while the second tuple element is a JSON record of song metadata.
  val testInput =
      (0, """{ "song_id" : "song-0", "song_name" : "song name-0", "artist_name" : "artist-1", """ +
      """"album_name" : "album-1", "genre" : "genre5.0", "tempo" : "100", """ +
      """"duration" : "240" }""") :: Nil

  /**
   * Validates the output generated by a test of the song metadata importer.
   *
   * This function accepts the output of a test as a buffer of tuples, where the first tuple element
   * is an entity id for a row that was written to by the job, and the second tuple element is a
   * sequence of SongMetadata records that were written. We validate the data that should have been
   * written for the single row imported.
   *
   * @param generatedMetadata contains a tuple for each row written to by the importer.
   */
  def validateTest(generatedMetadata: Buffer[(EntityId, Seq[FlowCell[GenericRecord]])]) {
    assert(1 === generatedMetadata.size)
    // Get the first song metadata record written.
    val metadata = generatedMetadata(0)._2.head.datum
    // And confirm it contains the fields we expect.
    assert("song name-0" === metadata.get("song_name").toString)
    assert("artist-1" === metadata.get("artist_name").toString)
    assert("album-1" === metadata.get("album_name").toString)
    assert("genre5.0" === metadata.get("genre").toString)
    assert(100L === metadata.get("tempo").asInstanceOf[Long])
    assert(240L === metadata.get("duration").asInstanceOf[Long])
  }

  // Run a test of the import job, running in Cascading's local runner.
  test("SongMetadataImporter processes JSON metadata into Avro records using local mode.") {
    JobTest(new SongMetadataImporter(_))
        .arg("table-uri", tableURI)
        .arg("input", "song-metadata.json")
        .source(TextLine("song-metadata.json"), testInput)
        .sink(KijiOutput.builder
            .withTableURI(tableURI)
            .withColumns('metadata -> "info:metadata")
            .build
        )(validateTest)
        .run
        .finish
  }

  // Run a test of the import job, running in Hadoop's local job runner.
  test("SongMetadataImporter processes JSON metadata into Avro records using Hadoop mode.") {
    JobTest(new SongMetadataImporter(_))
        .arg("table-uri", tableURI)
        .arg("input", "song-metadata.json")
        .source(TextLine("song-metadata.json"), testInput)
        .sink(KijiOutput.builder
            .withTableURI(tableURI)
            .withColumns('metadata -> "info:metadata")
            .build
        )(validateTest)
        .runHadoop
        .finish
  }
}