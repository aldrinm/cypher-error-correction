package aldrinm.agent;

import aldrinm.agent.cyver.CyverSyntaxValidatonResult;


public record ValidationReport(
        CyverSyntaxValidatonResult syntaxResult,
        CyverSyntaxValidatonResult schemaResult,
        CyverSyntaxValidatonResult propertiesResult
) {
}
