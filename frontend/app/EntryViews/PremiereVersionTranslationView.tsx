import React, { useEffect, useState } from "react";
import axios from "axios";
import {
  SystemNotifcationKind,
  SystemNotification,
} from "@guardian/pluto-headers";

interface PremiereVersionTranslationViewProps {
  internalVersion: number;
}

const PremiereVersionTranslationView: React.FC<
  PremiereVersionTranslationViewProps
> = (props) => {
  const [translation, setTranslation] = useState<
    PremiereVersionTranslation | undefined
  >(undefined);
  const [versionNotFound, setVersionNotFound] = useState(false);

  useEffect(() => {
    const loadData = async () => {
      try {
        const response = await axios.get(
          `/api/premiereVersion/internal/${props.internalVersion}`,
          { validateStatus: (status) => status === 200 || status == 404 }
        );
        switch (response.status) {
          case 200:
            const result = response.data.version as PremiereVersionTranslation;
            setTranslation(result);
            break;
          case 404:
            setVersionNotFound(true);
            break;
          default:
            console.log(
              `The server returned an unexpected response ${response.status} ${response.statusText} when looking up internal version ${props.internalVersion}, this should not happen`
            );
            SystemNotification.open(
              SystemNotifcationKind.Error,
              "Unexpected response was not flagged as an error, this is a frontend bug. Please report this message to multimediatech@theguardian.com"
            );
            break;
        }
      } catch (e) {
        console.error(
          `Could not look up version translation for ${props.internalVersion}: `,
          e
        );
        SystemNotification.open(
          SystemNotifcationKind.Error,
          "There was an error looking up the Premiere version translation, see console logs for details"
        );
      }
    };

    loadData();
  }, [props.internalVersion]);

  if (translation) {
    return (
      <span>
        {translation.name} {translation.displayedVersion}
      </span>
    );
  } else if (versionNotFound) {
    return <span>unrecognised version {props.internalVersion}</span>;
  } else {
    return <span>internal version {props.internalVersion}</span>;
  }
};

export default PremiereVersionTranslationView;
