/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.geode.cache.lucene;

import static org.apache.geode.distributed.ConfigurationProperties.SERIALIZABLE_OBJECT_FILTER;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Objects;
import java.util.Properties;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.cache.query.Query;
import org.apache.geode.cache.query.QueryService;
import org.apache.geode.cache.query.SelectResults;
import org.apache.geode.internal.cache.InternalCache;
import org.apache.geode.test.dunit.rules.ClientVM;
import org.apache.geode.test.dunit.rules.ClusterStartupRule;
import org.apache.geode.test.dunit.rules.MemberVM;
import org.apache.geode.test.junit.categories.DistributedTest;

@Category(DistributedTest.class)
public class LuceneNumericQueryDUnitTest {

  @ClassRule
  public static ClusterStartupRule clusterStartupRule = new ClusterStartupRule();

  private static MemberVM locator;
  private static MemberVM server1;
  private static ClientVM client1;

  private static final SomeDomain someDomain1 =
      SomeDomain.newBuilder()
          .withStrField("strField1")
          .withIntField(110)
          .withLongField(10010L)
          .withFloatField(101.25F)
          .withDoubleField(101.25D)
          .withDateField(
              Date.from(LocalDateTime.parse("2001-01-01T00:00:00").toInstant(ZoneOffset.UTC)))
          .build();

  private static final SomeDomain someDomain2 =
      SomeDomain.newBuilder()
          .withStrField("strField2")
          .withIntField(120)
          .withLongField(10020L)
          .withFloatField(201.25F)
          .withDoubleField(201.25D)
          .withDateField(
              Date.from(LocalDateTime.parse("2002-01-01T00:00:00").toInstant(ZoneOffset.UTC)))
          .build();

  private static final SomeDomain someDomain3 =
      SomeDomain.newBuilder()
          .withStrField("strField3")
          .withIntField(130)
          .withLongField(10030L)
          .withFloatField(301.25F)
          .withDoubleField(301.25D)
          .withDateField(
              Date.from(LocalDateTime.parse("2003-01-01T00:00:00").toInstant(ZoneOffset.UTC)))
          .build();


  @BeforeClass
  public static void beforeAllTests() throws Exception {
    locator = clusterStartupRule.startLocatorVM(0);
    final int locatorPort = locator.getPort();
    server1 = clusterStartupRule
        .startServerVM(1,
            r -> r.withPDXPersistent()
                .withPDXReadSerialized()
                .withConnectionToLocator(locatorPort)
                .withProperty(SERIALIZABLE_OBJECT_FILTER, "org.apache.geode.cache.lucene.**"));


    client1 =
        clusterStartupRule.startClientVM(2, new Properties(),
            (x -> x
                .set(SERIALIZABLE_OBJECT_FILTER, "org.apache.geode.cache.lucene.**")
                .addPoolLocator("localhost", locatorPort)));

    server1.invoke(() -> {
      InternalCache cache = ClusterStartupRule.getCache();
      LuceneService luceneService = LuceneServiceProvider.get(cache);
      luceneService.createIndexFactory()
          .setFields("strField", "intField", "dateField", "longField", "floatField", "doubleField")
          .create("idx1",
              "/sampleregion");

      cache.<String, SomeDomain>createRegionFactory(RegionShortcut.PARTITION)
              .create("sampleregion");
    });

    client1.invoke(() -> {
      ClientCache clientCache = ClusterStartupRule.getClientCache();

      Region<String, SomeDomain> region =
          clientCache.<String, SomeDomain>createClientRegionFactory(ClientRegionShortcut.PROXY)
              .create("sampleregion");

      region.put(someDomain1.getStrField(), someDomain1);
      region.put(someDomain2.getStrField(), someDomain2);
      region.put(someDomain3.getStrField(), someDomain3);
    });
  }


  @Test
  public void testByIntegerRange() {
    client1.invoke(() -> {
      ClientCache clientCache = ClusterStartupRule.getClientCache();

      QueryService queryService = clientCache.getQueryService();

      Query query = queryService.newQuery("select * from /sampleregion");
      SelectResults results = (SelectResults) query.execute();
      assertThat(results).hasSize(3);

      LuceneService luceneService = LuceneServiceProvider.get(clientCache);

      LuceneQuery luceneQuery1 = luceneService.createLuceneQueryFactory()
          .create("idx1", "/sampleregion", "+strField=strField* +intField:[110 TO 120]", "strField");

      assertThat(luceneQuery1.findKeys())
          .containsExactlyInAnyOrder("strField1", "strField2");

      assertThat(luceneQuery1.<SomeDomain>findValues())
          .containsExactlyInAnyOrder(someDomain1, someDomain2);


      LuceneQuery luceneQuery2 = luceneService.createLuceneQueryFactory()
          .create("idx1", "/sampleregion", "+strField=strField* +intField:[120 TO 130]", "strField");

      assertThat(luceneQuery2.findKeys())
          .containsExactlyInAnyOrder("strField2", "strField3");
      
      assertThat(luceneQuery2.<SomeDomain>findValues())
          .containsExactlyInAnyOrder(someDomain2, someDomain3);

    });
  }

  @Test
  public void testByLongRange() {
    client1.invoke(() -> {
      ClientCache clientCache = ClusterStartupRule.getClientCache();

      LuceneService luceneService = LuceneServiceProvider.get(clientCache);

      LuceneQuery luceneQuery1 = luceneService.createLuceneQueryFactory()
          .create("idx1", "/sampleregion", "+longField:[10010 TO 10025]", "strField");

      assertThat(luceneQuery1.findKeys())
          .containsExactlyInAnyOrder("strField1", "strField2");

      assertThat(luceneQuery1.<SomeDomain>findValues())
          .containsExactlyInAnyOrder(someDomain1, someDomain2);

      LuceneQuery luceneQuery2 = luceneService.createLuceneQueryFactory()
          .create("idx1", "/sampleregion", "+longField:[10011 TO 10030]", "strField");

      assertThat(luceneQuery2.findKeys())
          .containsExactlyInAnyOrder("strField2", "strField3");

      assertThat(luceneQuery2.<SomeDomain>findValues())
          .containsExactlyInAnyOrder(someDomain2, someDomain3);
    });
  }

  @Test
  public void testByFloatRange() {
    client1.invoke(() -> {
      ClientCache clientCache = ClusterStartupRule.getClientCache();

      LuceneService luceneService = LuceneServiceProvider.get(clientCache);

      LuceneQuery luceneQuery1 = luceneService.createLuceneQueryFactory()
          .create("idx1", "/sampleregion", "+floatField:[100 TO 202]", "strField");

      assertThat(luceneQuery1.findKeys())
          .containsExactlyInAnyOrder("strField1", "strField2");

      assertThat(luceneQuery1.<SomeDomain>findValues())
          .containsExactlyInAnyOrder(someDomain1, someDomain2);
    });
  }

  @Test
  public void testByDoubleRange() {
    client1.invoke(() -> {
      ClientCache clientCache = ClusterStartupRule.getClientCache();

      LuceneService luceneService = LuceneServiceProvider.get(clientCache);

      LuceneQuery luceneQuery1 = luceneService.createLuceneQueryFactory()
          .create("idx1", "/sampleregion", "+doubleField:[100 TO 202]", "strField");

      assertThat(luceneQuery1.findKeys())
          .containsExactlyInAnyOrder("strField1", "strField2");

      assertThat(luceneQuery1.<SomeDomain>findValues())
          .containsExactlyInAnyOrder(someDomain1, someDomain2);
    });
  }  
}

class SomeDomain implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String strField;
  private final int intField;
  private final long longField;
  private final float floatField;
  private final double doubleField;
  private final Date dateField;

  public SomeDomain(String strField, int intField, long longField, float floatField,
                    double doubleField, Date dateField) {
    this.strField = strField;
    this.intField = intField;
    this.longField = longField;
    this.floatField = floatField;
    this.doubleField = doubleField;
    this.dateField = dateField;
  }

  private SomeDomain(Builder builder) {
    strField = builder.strField;
    intField = builder.intField;
    longField = builder.longField;
    floatField = builder.floatField;
    doubleField = builder.doubleField;
    dateField = builder.dateField;
  }

  public String getStrField() {
    return strField;
  }

  public int getIntField() {
    return intField;
  }

  public long getLongField() {
    return longField;
  }

  public float getFloatField() {
    return floatField;
  }

  public Date getDateField() {
    return dateField;
  }

  public double getDoubleField() {
    return doubleField;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  @Override
  public String toString() {
    final StringBuffer sb = new StringBuffer("SomeDomain{");
    sb.append("strField='").append(strField).append('\'');
    sb.append(", height=").append(intField);
    sb.append(", dateField=").append(dateField);
    sb.append('}');
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SomeDomain that = (SomeDomain) o;
    return intField == that.intField &&
        longField == that.longField &&
        Float.compare(that.floatField, floatField) == 0 &&
        Double.compare(that.doubleField, doubleField) == 0 &&
        Objects.equals(strField, that.strField) &&
        Objects.equals(dateField, that.dateField);
  }

  @Override
  public int hashCode() {

    return Objects.hash(strField, intField, longField, floatField, doubleField, dateField);
  }

  public static final class Builder {
    private String strField;
    private int intField;
    private long longField;
    private float floatField;
    private double doubleField;
    private Date dateField;

    private Builder() {
    }

    public Builder withStrField(String val) {
      strField = val;
      return this;
    }

    public Builder withIntField(int val) {
      intField = val;
      return this;
    }

    public Builder withLongField(long val) {
      longField = val;
      return this;
    }

    public Builder withFloatField(float val) {
      floatField = val;
      return this;
    }

    public Builder withDoubleField(double val) {
      doubleField = val;
      return this;
    }

    public Builder withDateField(Date val) {
      dateField = val;
      return this;
    }

    public SomeDomain build() {
      return new SomeDomain(this);
    }
  }
}
