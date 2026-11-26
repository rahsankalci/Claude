[
  [
    "name" : "AvrasyaService",
    "displayName" : "Avrasya Servis",
    "category" : "@@processName@@",
    "description" : "Avrasya ProgramID servisini REST ile cagirir (bkz. AvrasyaServiceWorkItemHandler)",
    "parameters" : [
      "ProgramID" : new StringDataType(),
      "ActivityName" : new StringDataType(),
      "Data" : new ObjectDataType("@@pkg@@.model.AvrasyaContainer")
    ],
    "results" : [
      "Data" : new ObjectDataType("@@pkg@@.model.AvrasyaContainer"),
      "ServiceError" : new StringDataType()
    ],
    "defaultHandler" : "mvel: new @@pkg@@.handler.AvrasyaServiceWorkItemHandler()",
    "icon" : "defaultservicenodeicon.png"
  ]
]
