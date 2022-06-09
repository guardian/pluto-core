import React, { useEffect, useState } from "react";
import axios from "axios";

interface ProjectTypeDisplayProps {
  projectTypeId: number;
}

const ProjectTypeDisplay: React.FC<ProjectTypeDisplayProps> = (props) => {
  const [projectType, setProjectType] = useState<string>("Unknown");

  const getProjectTypeName = async () => {
    try {
      const response = await axios.get(
        `/api/projecttype/${props.projectTypeId}`
      );
      setProjectType(response.data.result.name);
    } catch (err) {
      console.error("Could not load project type information: ", err);
    }
  };

  useEffect(() => {
    getProjectTypeName();
  }, []);

  return <>{projectType}</>;
};

export default ProjectTypeDisplay;
