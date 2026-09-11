// Minimal RFC4180-ish CSV support: quotes fields containing a comma/quote/newline, escapes
// quotes by doubling them, and parses that same shape back. Good enough for the flat,
// user-editable exports/imports this app needs — not a general-purpose CSV library.

export function toCsv(headers: string[], rows: string[][]): string {
  const escapeField = (field: string) => {
    if (/[",\n]/.test(field)) {
      return `"${field.replace(/"/g, '""')}"`;
    }
    return field;
  };
  const lines = [headers, ...rows].map((row) => row.map(escapeField).join(','));
  return lines.join('\n');
}

export function parseCsv(text: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let field = '';
  let inQuotes = false;

  for (let i = 0; i < text.length; i++) {
    const char = text[i];
    if (inQuotes) {
      if (char === '"') {
        if (text[i + 1] === '"') {
          field += '"';
          i++;
        } else {
          inQuotes = false;
        }
      } else {
        field += char;
      }
    } else if (char === '"') {
      inQuotes = true;
    } else if (char === ',') {
      row.push(field);
      field = '';
    } else if (char === '\n' || char === '\r') {
      if (char === '\r' && text[i + 1] === '\n') i++;
      row.push(field);
      field = '';
      if (row.some((f) => f.length > 0) || row.length > 1) rows.push(row);
      row = [];
    } else {
      field += char;
    }
  }
  if (field.length > 0 || row.length > 0) {
    row.push(field);
    rows.push(row);
  }
  return rows;
}

export function downloadCsv(filename: string, headers: string[], rows: string[][]) {
  const csv = toCsv(headers, rows);
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
