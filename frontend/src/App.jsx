import { useState } from 'react'

function App() {
  const [files, setFiles] = useState([])
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  async function classify() {
    if (!files.length) return

    setLoading(true)
    setError('')
    setResult(null)

    try {
      const body = new FormData()
      files.forEach(file => body.append('files', file))

      const response = await fetch('/api/classifications', {
        method: 'POST',
        body
      })

      if (!response.ok) {
        throw new Error(await response.text())
      }

      setResult(await response.json())
    } catch (e) {
      setError(e.message || 'Classification failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="shell">
      <section className="hero">
        <span className="eyebrow">JEV PROTOTYPE</span>
        <h1>Insurance Document Classifier</h1>
        <p>
          Upload a mixed set of PDF pages. The backend classifies them and
          detects where one logical document ends and the next begins.
        </p>
      </section>

      <section className="panel">
        <label className="upload">
          <span>Select PDF or image pages</span>
          <input
            type="file"
            multiple
            accept=".pdf,image/*"
            onChange={event => setFiles(Array.from(event.target.files || []))}
          />
        </label>

        {files.length > 0 && (
          <div className="file-list">
            {files.map(file => <div key={file.name}>{file.name}</div>)}
          </div>
        )}

        <button disabled={!files.length || loading} onClick={classify}>
          {loading ? 'Classifying…' : 'Classify & merge'}
        </button>

        {error && <div className="error">{error}</div>}
      </section>

      {result && (
        <section className="results">
          <div className="summary">
            <strong>{result.documentCount} logical documents</strong>
            <span>{result.pageCount} pages · engine: {result.decisionEngine}</span>
          </div>

          <div className="grid">
            {result.documents.map(document => (
              <article className="document-card" key={document.documentIndex}>
                <div className="document-card__header">
                  <span>Document {document.documentIndex}</span>
                  <strong>{document.documentType.replaceAll('_', ' ')}</strong>
                </div>

                <div className="confidence">
                  Classification confidence {Math.round(document.confidence * 100)}%
                </div>

                <div className="pages">
                  {document.pages.map(page => (
                    <div className="page" key={page.pageIndex}>
                      <div>
                        <strong>Page {page.pageIndex}</strong>
                        <span>{page.sourceName}</span>
                      </div>
                      {page.pageIndex > 1 && (
                        <span className="boundary">
                          same as previous {Math.round(page.sameAsPreviousProbability * 100)}%
                        </span>
                      )}
                    </div>
                  ))}
                </div>
              </article>
            ))}
          </div>
        </section>
      )}
    </main>
  )
}

export default App
