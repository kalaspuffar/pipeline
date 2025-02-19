<?xml version="1.0" encoding="UTF-8"?>
<p:declare-step xmlns:p="http://www.w3.org/ns/xproc" version="1.0"
                xmlns:px="http://www.daisy.org/ns/pipeline/xproc"
                xmlns:x="http://www.emc.com/documentum/xml/xproc"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:cx="http://xmlcalabash.com/ns/extensions"
                xmlns:c="http://www.w3.org/ns/xproc-step"
                exclude-inline-prefixes="#all"
                type="px:message" name="main">

    <p:documentation xmlns="http://www.w3.org/1999/xhtml">
        <p>Example usage:</p>
        <pre xml:space="preserve">
            &lt;px:message message="The value '$1' is an invalid font color. Will use black instead." severity="WARN"&gt;
                &lt;p:with-param name="param1" select="$color"/&gt;
            &lt;/px:message&gt;
        </pre>
    </p:documentation>

    <!-- see also: pipx (http://pipx.org/) -->

    <p:input port="source" primary="true" sequence="true">
        <p:empty/>
    </p:input>
    <p:output port="result" sequence="true">
        <p:pipe port="result" step="result"/>
    </p:output>

    <p:option name="severity" select="'INFO'"/>                  <!-- one of either: WARN, INFO, DEBUG. Defaults to "INFO". Use px:error to throw errors. -->
    <p:option name="message" required="true" cx:as="xs:string"/> <!-- message to be logged. $1, $2 etc will be replaced with the contents of param1, param2 etc. -->
    <p:option name="param1" select="''" cx:as="xs:string"/>
    <p:option name="param2" select="''" cx:as="xs:string"/>
    <p:option name="param3" select="''" cx:as="xs:string"/>
    <p:option name="param4" select="''" cx:as="xs:string"/>
    <p:option name="param5" select="''" cx:as="xs:string"/>
    <p:option name="param6" select="''" cx:as="xs:string"/>
    <p:option name="param7" select="''" cx:as="xs:string"/>
    <p:option name="param8" select="''" cx:as="xs:string"/>
    <p:option name="param9" select="''" cx:as="xs:string"/>
    <!-- in the unlikely event that you need more parameters you'll have to format the message string yourself -->

    <p:import href="http://xmlcalabash.com/extension/steps/library-1.0.xpl" use-when="p:system-property('p:product-name') = 'XML Calabash'">
        <p:documentation>
            cx:message
        </p:documentation>
    </p:import>
    <p:import href="error.xpl">
        <p:documentation>
            px:error
        </p:documentation>
    </p:import>
    
    <!--
        Calabash:
        <p:declare-step type="cx:message">
            <p:input port="source" sequence="true"/>
            <p:output port="result" sequence="true"/>
            <p:option name="message" required="true"/>
        </p:declare-step>
    -->

    <!--
        Calumet:
        <p:declare-step type="x:message">
            <p:option name="message" required="true"/>
            <p:option name="stderr" select="'true'"/>
            <p:option name="stdout" select="'false'"/>
            <p:input port="source" sequence="true"/>
            <p:output port="result" sequence="true"/>
        </p:declare-step>
    -->
    
    <!-- TODO: implement this in Java to make use of the logging levels there -->
    <p:declare-step type="px:log-message">
        <p:option name="message" required="true"/>
        <p:option name="severity" select="'INFO'"/>
        <p:input port="source" primary="true" sequence="true"/>
        <p:output port="result" sequence="true"/>
        <!--
            Implemented in ../../../java/org/daisy/pipeline/common/calabash/impl/Message.java
        -->
    </p:declare-step>

    <p:variable name="validSeverity" use-when="not(p:system-property('p:xpath-version')='1.0')" select="if ($severity=('WARN','INFO','DEBUG')) then $severity else 'INFO'">
        <p:inline>
            <irrelevant/>
        </p:inline>
    </p:variable>
    <p:variable name="validSeverity" use-when="p:system-property('p:xpath-version')='1.0'" select="concat(
        substring($severity, 1, number($severity='WARN' or $severity='INFO' or $severity='DEBUG') * string-length($severity)),
        substring('INFO', 1, number(not($severity='WARN' or $severity='INFO' or $severity='DEBUG')) * string-length('INFO'))
        )">
        <p:inline>
            <irrelevant/>
        </p:inline>
    </p:variable>
    
    <p:add-attribute match="/*" attribute-name="message" name="message">
        <p:input port="source">
            <p:inline>
                <c:result/>
            </p:inline>
        </p:input>
        <p:with-option name="attribute-value" use-when="p:system-property('p:xpath-version')='1.0'" select="$message">
            <!-- replace(...) not supported in XPath 1.0 -->
            <p:inline>
                <irrelevant/>
            </p:inline>
        </p:with-option>
        <p:with-option name="attribute-value" use-when="not(p:system-property('p:xpath-version')='1.0')"
            select="replace(replace(replace(replace(replace(replace(replace(replace(replace($message,'\$1',replace($param1,'\$','\\\$')),'\$2',replace($param2,'\$','\\\$')),'\$3',replace($param3,'\$','\\\$')),'\$4',replace($param4,'\$','\\\$')),'\$5',replace($param5,'\$','\\\$')),'\$6',replace($param6,'\$','\\\$')),'\$7',replace($param7,'\$','\\\$')),'\$8',replace($param8,'\$','\\\$')),'\$9',replace($param9,'\$','\\\$'))">
            <p:inline>
                <irrelevant/>
            </p:inline>
        </p:with-option>
    </p:add-attribute>
    <p:sink/>

    <p:identity>
        <p:input port="source">
            <p:pipe port="source" step="main"/>
        </p:input>
    </p:identity>
    <p:choose>
        <p:xpath-context>
            <p:inline>
                <irrelevant/>
            </p:inline>
        </p:xpath-context>
        
        <!-- Pipeline 2 -->
        <p:when test="p:step-available('px:log-message')">
            <px:log-message>
                <p:with-option name="message" select="/*/@message">
                    <p:pipe port="result" step="message"/>
                </p:with-option>
                <p:with-option name="severity" select="$validSeverity">
                    <p:inline>
                        <irrelevant/>
                    </p:inline>
                </p:with-option>
            </px:log-message>
        </p:when>

        <p:otherwise>
            
            <!-- Calabash -->
            <cx:message p:use-when="p:system-property('p:product-name') = 'XML Calabash'">
                <p:with-option name="message" select="concat('[',$validSeverity,'] ',/*/@message)">
                    <p:pipe port="result" step="message"/>
                </p:with-option>
            </cx:message>

            <!-- Calumet -->
            <x:message p:use-when="p:system-property('p:product-name') = 'Calumet'">
                <p:with-option name="message" select="concat('[',$validSeverity,'] ',/*/@message)">
                    <p:pipe port="result" step="message"/>
                </p:with-option>
                <p:with-option name="stderr" select="$validSeverity='WARN'">
                    <p:inline>
                        <irrelevant/>
                    </p:inline>
                </p:with-option>
                <p:with-option name="stdout" select="not($validSeverity='WARN')">
                    <p:inline>
                        <irrelevant/>
                    </p:inline>
                </p:with-option>
            </x:message>

            <!-- Other XProc processor -->
            <p:identity/>

        </p:otherwise>
    </p:choose>
    <p:identity name="result"/>

</p:declare-step>
