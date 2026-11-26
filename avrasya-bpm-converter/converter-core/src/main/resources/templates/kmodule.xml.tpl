<?xml version="1.0" encoding="UTF-8"?>
<kmodule xmlns="http://www.drools.org/xsd/kmodule" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <kbase name="@@kbase@@" default="true" eventProcessingMode="stream" equalsBehavior="identity"
         packages="@@pkg@@">
    <ksession name="@@ksession@@" type="stateful" default="true" clockType="realtime"/>
  </kbase>
</kmodule>
