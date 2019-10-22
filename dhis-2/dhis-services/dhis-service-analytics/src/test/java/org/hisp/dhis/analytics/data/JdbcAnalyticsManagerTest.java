package org.hisp.dhis.analytics.data;

/*
 * Copyright (c) 2004-2019, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import static org.hamcrest.Matchers.containsString;
import static org.hisp.dhis.DhisConvenienceTest.*;
import static org.hisp.dhis.common.DimensionalObject.*;
import static org.hisp.dhis.common.DimensionalObjectUtils.getList;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

import org.hisp.dhis.analytics.*;
import org.hisp.dhis.analytics.partition.PartitionManager;
import org.hisp.dhis.common.BaseDimensionalObject;
import org.hisp.dhis.common.DimensionType;
import org.hisp.dhis.common.ValueType;
import org.hisp.dhis.dataelement.DataElement;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.period.Period;
import org.hisp.dhis.period.PeriodType;
import org.hisp.dhis.setting.SystemSettingManager;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;

/**
 * @author Luciano Fiandesio
 */
public class JdbcAnalyticsManagerTest
{
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private SystemSettingManager systemSettingManager;

    @Mock
    private PartitionManager partitionManager;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private SqlRowSet rowSet;

    @Captor
    private ArgumentCaptor<String> sql;

    private JdbcAnalyticsManager subject;

    @Before
    public void setUp()
    {
        QueryPlanner queryPlanner = new DefaultQueryPlanner( new DefaultQueryValidator( this.systemSettingManager ),
            partitionManager );

        mockRowSet();

        when( jdbcTemplate.queryForRowSet( sql.capture() ) ).thenReturn( rowSet );

        subject = new JdbcAnalyticsManager( queryPlanner, jdbcTemplate );
    }

    @Test
    public void verifyQueryGeneratedWhenDataElementHasLastAggregationType()
    {
        DataQueryParams params = createParams( AggregationType.LAST );

        subject.getAggregatedDataValues( params, AnalyticsTableType.DATA_VALUE, 20000 );

        assertExpectedSql("desc");
    }

    @Test
    public void verifyQueryGeneratedWhenDataElementHasLastAvgOrgUnitAggregationType()
    {
        DataQueryParams params = createParams( AggregationType.LAST_AVERAGE_ORG_UNIT );

        subject.getAggregatedDataValues( params, AnalyticsTableType.DATA_VALUE, 20000 );

        assertExpectedSql("desc");
    }

    private void mockRowSet()
    {
        // Simulate no rows
        when( rowSet.next() ).thenReturn( false );
    }

    private DataQueryParams createParams(AggregationType aggregationType) {

        DataElement deA = createDataElement( 'A', ValueType.INTEGER, aggregationType );
        OrganisationUnit ouA = createOrganisationUnit( 'A' );
        Period peA = PeriodType.getPeriodFromIsoString( "201501" );

        return DataQueryParams.newBuilder().withDataType( DataType.NUMERIC )
                .withTableName( "analytics" )
                .withAggregationType( AnalyticsAggregationType.fromAggregationType( aggregationType ) )
                .addDimension( new BaseDimensionalObject( DATA_X_DIM_ID, DimensionType.DATA_X, getList( deA ) ) )
                .addFilter( new BaseDimensionalObject( ORGUNIT_DIM_ID, DimensionType.ORGANISATION_UNIT, getList( ouA ) ) )
                .addDimension( new BaseDimensionalObject( PERIOD_DIM_ID, DimensionType.PERIOD, getList( peA ) ) ).build();
    }

    private void assertExpectedSql(String sortOrder) {

        String lastAggregationTypeSql = "(select \"year\",\"pestartdate\",\"peenddate\",\"level\",\"daysxvalue\","
            + "\"daysno\",\"value\",\"textvalue\",\"dx\",cast('201501' as text) as \"pe\",\"ou\","
            + "row_number() over (partition by dx, ou, co, ao order by peenddate " + sortOrder + ", pestartdate "
            + sortOrder + ") as pe_rank "
            + "from analytics as ax where pestartdate >= '2005-01-31' and pestartdate <= '2015-01-31' "
            + "and (value is not null or textvalue is not null))";

        assertThat( sql.getValue(), containsString( lastAggregationTypeSql ) );
    }

}