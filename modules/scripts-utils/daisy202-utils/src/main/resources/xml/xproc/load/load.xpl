<?xml version="1.0" encoding="UTF-8"?>
<p:declare-step xmlns:p="http://www.w3.org/ns/xproc" version="1.0"
                xmlns:px="http://www.daisy.org/ns/pipeline/xproc"
                xmlns:d="http://www.daisy.org/ns/pipeline/data"
                xmlns:c="http://www.w3.org/ns/xproc-step"
                type="px:daisy202-load">

    <p:documentation>
        <p px:role="desc">Load a DAISY 2.02 fileset based on its NCC.</p>
    </p:documentation>

    <p:serialization port="fileset.out" indent="true"/>

    <p:option name="ncc" required="true">
        <p:documentation xmlns="http://www.w3.org/1999/xhtml">
            <p px:role="desc">URI to input NCC.</p>
        </p:documentation>
    </p:option>

    <p:output port="fileset.out" primary="true">
        <p:documentation>A fileset containing references to all the files in the DAISY 2.02 fileset
            and any resources they reference (images etc.). The base URI of each document points to
            the original file, while the href can change during conversions to reflect changes in
            the path and filename of the resulting file. The SMIL files in the fileset are ordered
            according the the "flow" (reading order).</p:documentation>
        <p:pipe port="fileset.out" step="wrapper"/>
    </p:output>

    <p:output port="in-memory.out" sequence="true">
        <p:documentation>The NCC file serialized as XHTML.</p:documentation>
        <p:pipe port="in-memory.out" step="wrapper"/>
    </p:output>

    <p:import href="http://www.daisy.org/pipeline/modules/common-utils/library.xpl">
        <p:documentation>
            px:message
        </p:documentation>
    </p:import>
    <p:import href="http://www.daisy.org/pipeline/modules/file-utils/library.xpl">
        <p:documentation>
            px:normalize-uri
        </p:documentation>
    </p:import>
    <p:import href="http://www.daisy.org/pipeline/modules/html-utils/library.xpl">
        <p:documentation>
            px:html-to-fileset
        </p:documentation>
    </p:import>
    <p:import href="http://www.daisy.org/pipeline/modules/fileset-utils/library.xpl">
        <p:documentation>
            px:fileset-create
            px:fileset-add-entry
            px:fileset-load
            px:fileset-join
        </p:documentation>
    </p:import>
    <p:import href="http://www.daisy.org/pipeline/modules/mediatype-utils/library.xpl">
        <p:documentation>
            px:mediatype-detect
        </p:documentation>
    </p:import>
    <p:import href="http://www.daisy.org/pipeline/modules/smil-utils/library.xpl">
        <p:documentation>
            px:smil-to-audio-fileset
            px:smil-to-text-fileset
        </p:documentation>
    </p:import>

    <px:normalize-uri name="ncc">
        <p:with-option name="href" select="$ncc"/>
    </px:normalize-uri>

    <p:group name="wrapper">
        <p:output port="fileset.out" primary="true">
            <p:pipe port="result" step="fileset"/>
        </p:output>
        <p:output port="in-memory.out" sequence="true">
            <p:pipe port="result" step="in-memory"/>
        </p:output>
        <p:variable name="href" select="/c:result/string()">
            <p:pipe step="ncc" port="normalized"/>
        </p:variable>
        <px:message severity="DEBUG">
            <p:with-option name="message" select="concat('loading NCC: ',$href)"/>
        </px:message>
        <p:sink/>

        <px:fileset-create/>
        <px:fileset-add-entry media-type="application/xhtml+xml">
            <p:with-option name="href" select="$href"/>
        </px:fileset-add-entry>
        <px:fileset-load name="in-memory.ncc"/>
        <px:message severity="DEBUG"
            message="Making an ordered list of SMIL-files referenced from the NCC according to the flow (reading order)"/>
        <p:xslt name="fileset.smil">
            <p:input port="parameters">
                <p:empty/>
            </p:input>
            <p:input port="stylesheet">
                <p:document href="ncc-to-smil-fileset.xsl"/>
            </p:input>
        </p:xslt>

        <px:message severity="DEBUG" message="Loading all SMIL files"/>
        <px:fileset-load>
            <p:input port="in-memory">
                <p:empty/>
            </p:input>
        </px:fileset-load>
        <p:identity name="in-memory.smil"/>

        <px:message severity="DEBUG" message="Listing all resources referenced from the SMIL files"/>
        <p:for-each>
            <p:output port="result" sequence="true">
                <p:pipe port="result" step="fileset.html-and-audio.audio"/>
                <p:pipe port="result" step="fileset.html-and-audio.text"/>
            </p:output>
            <p:identity name="smil"/>
            <px:smil-to-audio-fileset name="fileset.html-and-audio.audio"/>
            <p:sink/>
            <px:smil-to-text-fileset name="fileset.html-and-audio.text">
                <p:input port="source">
                    <p:pipe step="smil" port="result"/>
                </p:input>
            </px:smil-to-text-fileset>
            <p:sink/>
        </p:for-each>
        <px:fileset-join name="fileset.html-and-audio"/>

        <px:message severity="DEBUG" message="Loading all HTML-files"/>
        <p:delete>
            <p:with-option name="match" select="concat('d:file[resolve-uri(@href,base-uri())=&quot;',$href,'&quot;]')"/>
        </p:delete>
        <p:add-attribute match="d:file[matches(lower-case(@href),'\.x?html$')]"
                         attribute-name="media-type" attribute-value="application/xhtml+xml"/>
        <px:fileset-filter media-types="text/html application/xhtml+xml"/>
        <px:fileset-load/>
        <p:identity name="in-memory.html"/>

        <px:message severity="DEBUG" message="Listing all resources referenced from the HTML files"/>
        <p:for-each>
            <p:variable name="filename" select="replace(base-uri(/*),'^.*/','')"/>
            <px:html-to-fileset px:message="extracting list of resources from {$filename}" px:message-severity="DEBUG"/>
        </p:for-each>
        <px:fileset-join/>
        <!-- omit HTML files except those referenced from iframes -->
        <p:delete match="d:file[@media-type='application/xhtml+xml' and not(@kind='content')]"
                  name="fileset.html-resources"/>

        <p:identity name="in-memory">
            <p:input port="source">
                <p:pipe port="result" step="in-memory.ncc"/>
                <p:pipe port="result" step="in-memory.smil"/>
                <p:pipe port="result" step="in-memory.html"/>
            </p:input>
        </p:identity>

        <px:fileset-join>
            <p:input port="source">
                <p:pipe port="result" step="fileset.smil"/>
                <p:pipe port="result" step="fileset.html-and-audio"/>
                <p:pipe port="result" step="fileset.html-resources"/>
            </p:input>
        </px:fileset-join>
        <px:fileset-add-entry media-type="application/xhtml+xml" first="true">
            <p:with-option name="href" select="base-uri(.)">
                <p:pipe port="result" step="in-memory.ncc"/>
            </p:with-option>
        </px:fileset-add-entry>
        <px:fileset-join/>
        <px:mediatype-detect>
            <p:input port="in-memory">
                <p:pipe port="result" step="in-memory"/>
            </p:input>
        </px:mediatype-detect>
        <p:label-elements match="d:file[@media-type='application/xhtml+xml'][not(@media-version)]"
                          attribute="media-version"
                          label="'4.0'"/>
        <p:identity name="fileset"/>
    </p:group>
</p:declare-step>
