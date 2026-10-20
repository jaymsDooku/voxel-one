"""The distributable site must contain a reachable, complete trading client."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]

class StreetSiteTest(unittest.TestCase):
    def test_home_links_and_runtime_are_packaged(self):
        site = ROOT / 'website/dist'
        home = (site / 'index.html').read_text()
        self.assertIn('href="games/voxel-street/index.html"', home)
        self.assertIn('href="games/space-invaders/index.html"', home)
        game = site / 'games/voxel-street'
        self.assertEqual({p.name for p in game.iterdir()},
                         {'index.html', 'app.mjs', 'market.mjs', 'style.css'})
        for name in ('index.html', 'app.mjs', 'market.mjs', 'style.css'):
            self.assertEqual((game / name).read_bytes(),
                             (ROOT / 'games/voxel-street' / name).read_bytes())
        for ref in re.findall(r'(?:src|href)="([^\"]+)"', (game / 'index.html').read_text()):
            if not ref.startswith(('http', '#', 'data:')):
                self.assertTrue((game / ref).is_file(), ref)

if __name__ == '__main__':
    unittest.main()
