/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.gemstone.gemfire.cache.lucene;

import static org.junit.Assert.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionShortcut;
import com.gemstone.gemfire.cache.lucene.LuceneIndex;
import com.gemstone.gemfire.cache.lucene.LuceneIntegrationTest;
import com.gemstone.gemfire.cache.lucene.LuceneServiceProvider;
import com.gemstone.gemfire.cache.lucene.internal.LuceneIndexForPartitionedRegion;
import com.gemstone.gemfire.internal.cache.BucketNotFoundException;
import com.gemstone.gemfire.internal.cache.LocalRegion;
import com.gemstone.gemfire.test.junit.categories.IntegrationTest;
import com.jayway.awaitility.Awaitility;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(IntegrationTest.class)
public class LuceneIndexCreationIntegrationTest extends LuceneIntegrationTest {
  public static final String INDEX_NAME = "index";
  public static final String REGION_NAME = "region";

  @Test
  public void shouldCreateIndexWriterWithAnalyzersWhenSettingPerFieldAnalyzers()
    throws BucketNotFoundException, InterruptedException
  {
    Map<String, Analyzer> analyzers = new HashMap<>();

    final RecordingAnalyzer field1Analyzer = new RecordingAnalyzer();
    final RecordingAnalyzer field2Analyzer = new RecordingAnalyzer();
    analyzers.put("field1", field1Analyzer);
    analyzers.put("field2", field2Analyzer);
    luceneService.createIndex(INDEX_NAME, REGION_NAME, analyzers);
    Region region = createRegion();
    final LuceneIndex index = luceneService.getIndex(INDEX_NAME, REGION_NAME);
    region.put("key1", new TestObject());

    assertEquals(analyzers, index.getFieldAnalyzers());
    Awaitility.await().atMost(60, TimeUnit.SECONDS).until(() -> {
      assertEquals(Arrays.asList("field1"), field1Analyzer.analyzedfields);
      assertEquals(Arrays.asList("field2"), field2Analyzer.analyzedfields);
    });
  }

  @Test
  public void verifyLuceneRegionInternal() {
    createIndex("text");

    // Create partitioned region
    createRegion();

    // Get index
    LuceneIndexForPartitionedRegion index = (LuceneIndexForPartitionedRegion) luceneService.getIndex(INDEX_NAME, REGION_NAME);

    // Verify the meta regions exist and are internal
    LocalRegion chunkRegion = (LocalRegion) cache.getRegion(index.createChunkRegionName());
    assertTrue(chunkRegion.isInternalRegion());
    LocalRegion fileRegion = (LocalRegion) cache.getRegion(index.createFileRegionName());
    assertTrue(fileRegion.isInternalRegion());

    // Verify the meta regions are not contained in the root regions
    for (Region region : cache.rootRegions()) {
      assertNotEquals(chunkRegion.getFullPath(), region.getFullPath());
      assertNotEquals(fileRegion.getFullPath(), region.getFullPath());
    }
  }

  private Region createRegion() {
    return this.cache.createRegionFactory(RegionShortcut.PARTITION).create(REGION_NAME);
  }

  private void createIndex(String fieldName) {
    LuceneServiceProvider.get(this.cache).createIndex(INDEX_NAME, REGION_NAME, fieldName);
  }

  private static class TestObject implements Serializable {

    String field1 = "a b c d";
    String field2 = "f g h";
  }

  private static class RecordingAnalyzer extends Analyzer {

    private List<String> analyzedfields = new ArrayList<String>();

    @Override protected TokenStreamComponents createComponents(final String fieldName) {
      analyzedfields.add(fieldName);
      return new TokenStreamComponents(new KeywordTokenizer());
    }
  }
}
