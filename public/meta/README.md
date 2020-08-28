This directory is only present for running locally during development.
The app serves it under the /meta root, which is where
pluto-start presents the common data elements for the menu etc.

We serve it like this so that the menu will still work even
when we are running locally.

The menu.json file is customised to remove the /pluto-core prefix
from all the links.