package org.hisp.dhis.dxf2.events.event.validation;

/*
 * Copyright (c) 2004-2020, University of Oslo
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

import static org.hisp.dhis.dxf2.importsummary.ImportSummary.success;

import org.hisp.dhis.common.IdScheme;
import org.hisp.dhis.dxf2.events.event.context.WorkContext;
import org.hisp.dhis.dxf2.importsummary.ImportStatus;
import org.hisp.dhis.dxf2.importsummary.ImportSummary;
import org.hisp.dhis.program.ProgramInstance;
import org.hisp.dhis.program.ProgramStage;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * @author Luciano Fiandesio
 */
public class ProgramInstanceRepeatableStageCheck implements ValidationCheck
{
    @Override
    public ImportSummary check( ImmutableEvent event, WorkContext ctx )
    {
        IdScheme scheme = ctx.getImportOptions().getIdSchemes().getProgramStageIdScheme();
        ProgramStage programStage = ctx.getProgramStage( scheme, event.getProgramStage() );
        ProgramInstance programInstance = ctx.getProgramInstanceMap().get( event.getUid() );

        /*
         * ProgramInstance should never be null. If it's null, the ProgramInstanceCheck
         * should report this anomaly.
         */
        if ( programInstance != null )
        {
            if ( !programStage.getRepeatable()
                && hasProgramStageInstance( ctx.getServiceDelegator().getJdbcTemplate(), programStage.getUid() ) )
            {
                return new ImportSummary( ImportStatus.ERROR,
                    "Program stage is not repeatable and an event already exists" ).setReference( event.getEvent() )
                        .incrementIgnored();
            }
        }

        return success();
    }

    private boolean hasProgramStageInstance( JdbcTemplate jdbcTemplate, String programStageUid )
    {
        // @formatter:off
        final String sql = "select exists( " +
                "select * " +
                "from programstageinstance psi " +
                "  join programinstance pi on psi.programinstanceid = pi.programinstanceid " +
                "  join programstage ps on psi.programstageid = ps.programstageid " +
                "where ps.uid = ? " +
                "  and psi.deleted = false " +
                "  and psi.status != 'SKIPPED'" +
                ")";
        // @formatter:on

        return jdbcTemplate.queryForObject( sql, Boolean.class, programStageUid );
    }

    @Override
    public boolean isFinal()
    {
        return false;
    }
}
