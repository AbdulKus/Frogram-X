"""Run the production SQLite queries against real SQLite, including retention boundaries."""
from pathlib import Path
import re
import sqlite3
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SOURCE = (ROOT / 'app/src/main/java/org/thunderdog/challegram/telegram/MessageRevisionStore.java').read_text()

class RevisionQueriesTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        for sql in re.findall(r'db.execSQL\("(CREATE [^"]+)"\)', SOURCE):
            self.db.execute(sql)
        self.latest, self.history = re.findall(r'rawQuery\("([^"]+)"', SOURCE)

    def tearDown(self):
        self.db.close()

    def insert(self, account, chat, message, observed, text):
        self.db.execute('INSERT INTO revisions(account,chat,message,observed,text) VALUES (?,?,?,?,?)', (account,chat,message,observed,text))

    def test_account_chat_message_isolation(self):
        self.insert(1,2,3,100,'one')
        self.insert(2,2,3,100,'other account')
        self.insert(1,4,3,100,'other chat')
        self.insert(1,2,5,100,'other message')
        self.assertEqual(self.db.execute(self.latest, ('1','2','3')).fetchall(), [('one',)])
        self.assertEqual(self.db.execute(self.history, ('1','2','3')).fetchall(), [(100,'one')])

    def test_sequence_orders_same_timestamp_and_clock_rollback(self):
        for stamp, text in [(100,'original'),(100,'edit'),(50,'clock rolled back')]:
            self.insert(1,2,3,stamp,text)
        self.assertEqual(self.db.execute(self.latest, ('1','2','3')).fetchone()[0], 'clock rolled back')
        self.assertEqual([x[1] for x in self.db.execute(self.history, ('1','2','3'))], ['clock rolled back','edit','original'])

    def test_history_window(self):
        for i in range(150):
            self.insert(1,2,3,i,str(i))
        rows = self.db.execute(self.history, ('1','2','3')).fetchall()
        self.assertEqual(len(rows),100)
        self.assertEqual((rows[0][1], rows[-1][1]), ('149','50'))

    def test_disk_cap_preserves_newest(self):
        for i in range(10100):
            self.insert(i%2,2,3,i,str(i))
        prune = re.search(r'db.execSQL\("(DELETE FROM revisions[^"]+)"\)', SOURCE)[1]
        self.db.execute(prune)
        self.assertEqual(self.db.execute('SELECT COUNT(*), MIN(observed), MAX(observed) FROM revisions').fetchone(), (10000,100,10099))

    def test_unicode_empty_caption_and_multiline(self):
        for text in ['Привет 👋\n世界', '', "apostrophe ' and SQL ?; DELETE FROM revisions"]:
            self.insert(1,2,3,100,text)
        rows = list(self.db.execute(self.history,('1','2','3')))
        self.assertEqual(rows[-1][1],'Привет 👋\n世界')
        self.assertEqual(rows[1][1],'')
        self.assertEqual(len(rows),3)

    def test_message_and_account_deletion(self):
        self.insert(1,2,3,100,'one')
        self.insert(2,2,3,100,'two')
        deletion = re.search(r'database\(\).delete\("revisions", "(account=\? AND chat=\? AND message=\?)"', SOURCE)[1]
        self.db.execute('DELETE FROM revisions WHERE '+deletion,(1,2,3))
        self.assertEqual(self.db.execute(self.history,('1','2','3')).fetchall(),[])
        self.assertEqual(len(self.db.execute(self.history,('2','2','3')).fetchall()),1)

    def test_retention_boundary(self):
        self.insert(1,2,3,99,'expired')
        self.insert(1,2,3,100,'boundary')
        where = re.search(r'db.delete\("revisions", "(observed < \?)"',SOURCE)[1]
        self.db.execute('DELETE FROM revisions WHERE '+where,(100,))
        self.assertEqual(self.db.execute(self.history,('1','2','3')).fetchall(),[(100,'boundary')])

class ResourcesTest(unittest.TestCase):
    def test_translation_keys_and_format_arguments_match(self):
        base = ROOT/'app/src/main/res'
        def entries(directory):
            return {e.attrib['name']:e.text for e in ET.parse(base/directory/'frogram_messages.xml').getroot() if e.tag=='string'}
        english, russian = entries('values'), entries('values-ru')
        self.assertEqual(english.keys(),russian.keys())
        for key in english:
            self.assertEqual(re.findall(r'%\d+\$[a-z]',english[key]), re.findall(r'%\d+\$[a-z]',russian[key]),key)

if __name__ == '__main__':
    unittest.main()
