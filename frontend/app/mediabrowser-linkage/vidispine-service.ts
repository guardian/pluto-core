import axios from "axios";
import { VidispineItem } from "./vidispine/item/VidispineItem";
import { VError } from "ts-interface-checker";
import { SystemNotification, SystemNotifcationKind } from "pluto-headers";

/**
 * validates a given vidispine item, returning either a VidispineItem or undefined if it fails to validate.
 * error message is output to console if it fails.
 * @param content object to verify
 */
const validateVSItem = (content: any) => {
  try {
    return new VidispineItem(content);
  } catch (err) {
    if (err instanceof VError) {
      const vErr = err as VError;

      const itemId = content.id ?? "(no id given)";
      console.error(
        `Item ${itemId} failed metadata validation at ${vErr.path}: ${vErr.message}`
      );
    } else {
      console.error("Unexpected error: ", err);
    }
    return undefined;
  }
};

async function assetsForProject(
  vidispineBaseUrl: string,
  projectId: number,
  fromCount: number,
  pageSize: number
): Promise<VidispineItem[]> {
  const standardSearchDocument = `
<ItemSearchDocument xmlns="http://xml.vidispine.com/schema/vidispine">
    <field>gnm_containing_projects</field>
    <value>${projectId}</value>
    <sort>
        <field>durationSeconds</field>
        <order>descending</order>
    </sort>
</ItemSearchDocument>
    `;

  const fieldList = [
    "title",
    "representativeThumbnailNoAuth",
    "gnm_category",
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

  switch (response.status) {
    case 200:
      if (response.data.item) {
        return response.data.item
          .map(validateVSItem)
          .filter((item: VidispineItem | undefined) => item !== undefined);
      } else {
        return [];
      }
    case 400:
      console.log("vidispine returned bad request: ", response.data);
      return Promise.reject("Internal error: vidispine search was incorrect");
    case 500 | 502 | 503 | 504:
      console.log(
        "vidispine server error ",
        response.status,
        ": ",
        response.data
      );
      return Promise.reject("vidispine is not responding correctly");
    default:
      console.log("vidispine returned ", response.status, ": ", response.data);
      return Promise.reject("vidispine returned an unexpected error");
  }
}

export { assetsForProject };
