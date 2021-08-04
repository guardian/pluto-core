import axios from "axios";

async function assetsForProject(
  vidispineBaseUrl: string,
  projectId: number,
  fromCount: number,
  pageSize: number
) {
  const standardSearchDocument = `
        <ItemSearchDocument xmlns="http://xml.vidispine.com/schema/vidispine">
            <field name="gnm_containing_projects" value="${projectId}"/>
            <sort>
                <field>durationSeconds</field>
                <order>descending</order>
            </sort>
        </ItemSearchDocument>
    `;

  const fieldList = [
    "durationSeconds",
    "originalWidth",
    "originalHeight",
    "originalFilename",
  ].join(",");
  const response = await axios.put(
    `${vidispineBaseUrl}/API/item?content=metadata&field=${fieldList}&first=${fromCount}&number=${pageSize}&count=true`,
    standardSearchDocument,
    {
      headers: {
        "Content-Type": "application/xml",
        Accept: "application/json",
      },
      validateStatus: () => true,
    }
  );
}
