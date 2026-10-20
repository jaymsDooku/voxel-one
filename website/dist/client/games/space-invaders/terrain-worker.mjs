import {buildTerrainScene} from './terrain-scene.mjs';
self.onmessage = ({data}) => {
  try {
    const scene = buildTerrainScene(data.config, data.view, data.edits);
    self.postMessage({id: data.id, scene});
  } catch (error) {
    self.postMessage({id: data.id, error: error.message});
  }
};
