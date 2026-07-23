import { createServer } from 'http'
import { randomUUID } from 'crypto'
import { createWriteStream, readFileSync, unlinkSync, readdirSync, mkdirSync, writeFileSync } from 'fs'
import { execSync, exec } from 'child_process'
import { tmpdir } from 'os'
import { join, extname } from 'path'

const PORT = parseInt(process.env.PORT || '3000')
const TMP = join(tmpdir(), 'lo-sidecar')

mkdirSync(TMP, { recursive: true })

function run(cmd) {
  return new Promise((resolve, reject) => {
    exec(cmd, { timeout: 60000 }, (err, stdout, stderr) => {
      if (err) reject(new Error(stderr || stdout))
      else resolve(stdout)
    })
  })
}

function parseForm(body, contentType) {
  const boundary = contentType.split('boundary=')[1]
  if (!boundary) throw new Error('No boundary')

  const parts = body.toString('binary').split(`--${boundary}`)
  const filePart = parts.find(p => p.includes('Content-Disposition: form-data; name="file"'))
  if (!filePart) throw new Error('No file part')

  const headerEnd = filePart.indexOf('\r\n\r\n')
  const data = filePart.slice(headerEnd + 4, filePart.lastIndexOf('\r\n'))
  const match = filePart.match(/filename="(.+)"/)
  const filename = match ? match[1] : 'file'
  return { data: Buffer.from(data, 'binary'), filename }
}

const server = createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*')
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type')

  if (req.method === 'OPTIONS') { res.writeHead(204); res.end(); return }

  if (req.method === 'GET' && req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify({ status: 'ok' }))
    return
  }

  if (req.method === 'POST' && req.url === '/convert/html') {
    const chunks = []
    req.on('data', c => chunks.push(c))
    req.on('end', async () => {
      try {
        const body = Buffer.concat(chunks)
        const { data, filename } = parseForm(body, req.headers['content-type'])
        const id = randomUUID()
        const inPath = join(TMP, `${id}_${filename}`)
        const outDir = join(TMP, id)

        writeFileSync(inPath, data)
        mkdirSync(outDir, { recursive: true })
        await run(`libreoffice --headless --convert-to html --outdir ${outDir} ${inPath}`)

        const outFiles = readdirSync(outDir).filter(f => f.endsWith('.html'))
        if (outFiles.length === 0) throw new Error('Conversion failed')

        const html = readFileSync(join(outDir, outFiles[0]), 'utf-8')

        unlinkSync(inPath)
        execSync(`rm -rf ${outDir}`)

        res.writeHead(200, { 'Content-Type': 'text/html' })
        res.end(html)
      } catch (err) {
        res.writeHead(500, { 'Content-Type': 'application/json' })
        res.end(JSON.stringify({ error: err.message }))
      }
    })
    return
  }

  if (req.method === 'POST' && req.url === '/convert/from-html') {
    const chunks = []
    req.on('data', c => chunks.push(c))
    req.on('end', async () => {
      try {
        const body = Buffer.concat(chunks)
        const { data, filename } = parseForm(body, req.headers['content-type'])
        const id = randomUUID()
        const inPath = join(TMP, `${id}.html`)
        const outDir = join(TMP, id)

        const ext = extname(filename || 'output.docx').slice(1) || 'docx'

        writeFileSync(inPath, data)
        mkdirSync(outDir, { recursive: true })

        const filterMap = {
          docx: 'docx:Office Open XML Text', doc: 'doc:MS Word 97', odt: 'odt:writer8',
          rtf: 'rtf:Rich Text Format', xlsx: 'xlsx:Calc MS Excel 2007 XML', xls: 'xls:MS Excel 97',
          ods: 'ods:calc8', pptx: 'pptx:Impress MS PowerPoint 2007 XML', ppt: 'ppt:MS PowerPoint 97',
          odp: 'odp:impress8', csv: 'csv:Text - txt - csv (StarCalc)',
        }
        const filter = filterMap[ext] || ext
        await run(`libreoffice --headless --convert-to "${filter}" --outdir ${outDir} ${inPath}`)

        const outFiles = readdirSync(outDir).filter(f => !f.endsWith('.html'))
        if (outFiles.length === 0) throw new Error('Conversion failed')

        const outPath = join(outDir, outFiles[0])
        const output = readFileSync(outPath)

        unlinkSync(inPath)
        execSync(`rm -rf ${outDir}`)

        res.writeHead(200, {
          'Content-Type': 'application/octet-stream',
          'Content-Disposition': `attachment; filename="converted.${ext}"`,
        })
        res.end(output)
      } catch (err) {
        res.writeHead(500, { 'Content-Type': 'application/json' })
        res.end(JSON.stringify({ error: err.message }))
      }
    })
    return
  }

  res.writeHead(404); res.end()
})

server.listen(PORT, () => console.log(`LO sidecar listening on port ${PORT}`))
