/*
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
package io.prestosql.sql.planner;

import com.google.common.collect.ImmutableList;
import io.prestosql.metadata.Metadata;
import io.prestosql.spi.predicate.Domain;
import io.prestosql.spi.predicate.ValueSet;
import io.prestosql.spi.type.Type;
import io.prestosql.spi.type.TypeOperators;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static io.prestosql.SessionTestUtils.TEST_SESSION;
import static io.prestosql.metadata.MetadataManager.createTestMetadataManager;
import static io.prestosql.spi.predicate.Domain.multipleValues;
import static io.prestosql.spi.predicate.Range.greaterThan;
import static io.prestosql.spi.predicate.Range.greaterThanOrEqual;
import static io.prestosql.spi.predicate.Range.lessThan;
import static io.prestosql.spi.predicate.Range.lessThanOrEqual;
import static io.prestosql.spi.predicate.Range.range;
import static io.prestosql.spi.type.BigintType.BIGINT;
import static io.prestosql.spi.type.DecimalType.createDecimalType;
import static io.prestosql.spi.type.DoubleType.DOUBLE;
import static io.prestosql.spi.type.IntegerType.INTEGER;
import static io.prestosql.spi.type.RealType.REAL;
import static io.prestosql.spi.type.SmallintType.SMALLINT;
import static java.lang.Float.floatToIntBits;
import static org.testng.Assert.assertEquals;

public class TestDomainCoercer
{
    private Metadata metadata;
    private TypeOperators typeOperators;

    @BeforeClass
    public void setup()
    {
        metadata = createTestMetadataManager();
        typeOperators = new TypeOperators();
    }

    @AfterClass(alwaysRun = true)
    public void tearDown()
    {
        metadata = null;
    }

    @Test
    public void testNone()
    {
        assertEquals(applySaturatedCasts(Domain.none(BIGINT), INTEGER), Domain.none(INTEGER));
    }

    @Test
    public void testAll()
    {
        assertEquals(applySaturatedCasts(Domain.all(BIGINT), INTEGER), Domain.all(INTEGER));
    }

    @Test
    public void testOnlyNull()
    {
        assertEquals(applySaturatedCasts(Domain.onlyNull(BIGINT), INTEGER), Domain.onlyNull(INTEGER));
    }

    @Test
    public void testCoercedValueSameAsOriginal()
    {
        assertEquals(
                applySaturatedCasts(multipleValues(BIGINT, ImmutableList.of(1L, 10000L, -2000L)), SMALLINT),
                multipleValues(SMALLINT, ImmutableList.of(1L, 10000L, -2000L)));

        Domain original = Domain.create(
                ValueSet.ofRanges(
                        lessThan(DOUBLE, 0.0),
                        range(DOUBLE, 0.0, false, 1.0, false),
                        range(DOUBLE, 2.0, true, 3.0, true),
                        greaterThan(DOUBLE, 4.0)),
                true);
        assertEquals(
                applySaturatedCasts(original, REAL),
                Domain.create(
                        ValueSet.ofRanges(
                                lessThan(REAL, (long) floatToIntBits(0.0f)),
                                range(REAL, (long) floatToIntBits(0.0f), false, (long) floatToIntBits(1.0f), false),
                                range(REAL, (long) floatToIntBits(2.0f), true, (long) floatToIntBits(3.0f), true),
                                greaterThan(REAL, (long) floatToIntBits(4.0f))),
                        true));
    }

    @Test
    public void testOutsideTargetTypeRange()
    {
        assertEquals(
                applySaturatedCasts(multipleValues(BIGINT, ImmutableList.of(1L, 10000000000L, -2000L)), SMALLINT),
                multipleValues(SMALLINT, ImmutableList.of(1L, -2000L)));

        assertEquals(
                applySaturatedCasts(
                        Domain.create(
                                ValueSet.ofRanges(range(DOUBLE, 0.0, true, ((double) Float.MAX_VALUE) * 10, true)),
                                true),
                        REAL),
                Domain.create(
                        ValueSet.ofRanges((range(REAL, (long) floatToIntBits(0.0f), true, (long) floatToIntBits(Float.MAX_VALUE), true))),
                        true));

        // low below and high above target type range
        assertEquals(
                applySaturatedCasts(
                        Domain.create(
                                ValueSet.ofRanges(
                                        range(DOUBLE, ((double) Float.MAX_VALUE) * -2, true, ((double) Float.MAX_VALUE) * 10, true)),
                                true),
                        REAL),
                Domain.create(ValueSet.ofRanges(lessThanOrEqual(REAL, (long) floatToIntBits(Float.MAX_VALUE))), true));

        assertEquals(
                applySaturatedCasts(
                        Domain.create(
                                ValueSet.ofRanges(
                                        range(DOUBLE, Double.NEGATIVE_INFINITY, true, Double.POSITIVE_INFINITY, true)),
                                true),
                        REAL),
                Domain.create(
                        ValueSet.ofRanges(
                                lessThanOrEqual(REAL, (long) floatToIntBits(Float.MAX_VALUE))),
                        true));

        assertEquals(
                applySaturatedCasts(
                        Domain.create(
                                ValueSet.ofRanges(
                                        range(BIGINT, ((long) Integer.MAX_VALUE) * -2, false, ((long) Integer.MAX_VALUE) * 10, false)),
                                true),
                        INTEGER),
                Domain.create(ValueSet.ofRanges(lessThanOrEqual(INTEGER, (long) Integer.MAX_VALUE)), true));

        assertEquals(
                applySaturatedCasts(
                        Domain.create(
                                ValueSet.ofRanges(
                                        range(DOUBLE, Double.NEGATIVE_INFINITY, true, Double.POSITIVE_INFINITY, true)),
                                true),
                        INTEGER),
                Domain.create(ValueSet.ofRanges(lessThanOrEqual(INTEGER, (long) Integer.MAX_VALUE)), true));

        // Low and high below target type range
        assertEquals(
                applySaturatedCasts(
                        Domain.create(
                                ValueSet.ofRanges(
                                        range(BIGINT, ((long) Integer.MAX_VALUE) * -4, false, ((long) Integer.MAX_VALUE) * -2, false)),
                                false),
                        INTEGER),
                Domain.none(INTEGER));

        assertEquals(
                applySaturatedCasts(
                        Domain.create(
                                ValueSet.ofRanges(
                                        range(DOUBLE, ((double) Float.MAX_VALUE) * -4, true, ((double) Float.MAX_VALUE) * -2, true)),
                                true),
                        REAL),
                Domain.onlyNull(REAL));

        // Low and high above target type range
        assertEquals(
                applySaturatedCasts(
                        Domain.create(
                                ValueSet.ofRanges(
                                        range(BIGINT, ((long) Integer.MAX_VALUE) * 2, false, ((long) Integer.MAX_VALUE) * 4, false)),
                                false),
                        INTEGER),
                Domain.none(INTEGER));

        assertEquals(
                applySaturatedCasts(
                        Domain.create(
                                ValueSet.ofRanges(
                                        range(DOUBLE, ((double) Float.MAX_VALUE) * 2, true, ((double) Float.MAX_VALUE) * 4, true)),
                                true),
                        REAL),
                Domain.onlyNull(REAL));

        // all short-circuit
        assertEquals(
                applySaturatedCasts(
                        Domain.create(
                                ValueSet.ofRanges(
                                        greaterThanOrEqual(DOUBLE, ((double) Float.MAX_VALUE) * -4),
                                        range(DOUBLE, 0.0, true, 1.0, true)),
                                true),
                        REAL),
                Domain.all(REAL));
    }

    @Test
    public void testTruncatedCoercedValue()
    {
        assertEquals(
                applySaturatedCasts(
                        Domain.create(
                                ValueSet.ofRanges(
                                        range(createDecimalType(6, 3), 123456L, true, 234567L, false)),
                                true),
                        createDecimalType(6, 1)),
                Domain.create(
                        ValueSet.ofRanges(range(createDecimalType(6, 1), 1234L, false, 2345L, true)),
                        true));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testUnsupportedCast()
    {
        applySaturatedCasts(Domain.singleValue(INTEGER, 10L), BIGINT);
    }

    private Domain applySaturatedCasts(Domain domain, Type coercedValueType)
    {
        return DomainCoercer.applySaturatedCasts(metadata, typeOperators, TEST_SESSION, domain, coercedValueType);
    }
}
