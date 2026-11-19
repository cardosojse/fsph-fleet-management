-- Preserve existing IDs, names and user assignments, including recognized aliases.
-- This catalog does not restrict roles or assign users to a unit.
WITH units(name, aliases) AS (VALUES
    ('Administrativo', ARRAY['administrativo', 'administracao', 'setor administrativo']),
    ('Superintendência Lacen', ARRAY['lacen', 'superintendencia lacen', 'superintendencia do lacen']),
    ('Superintendência Hemose', ARRAY['hemose', 'superintendencia hemose', 'superintendencia do hemose']),
    ('Superintendência SVO', ARRAY['svo', 'superintendencia svo', 'superintendencia do svo', 'servico de verificacao de obitos']),
    ('Coordenação de Infraestrutura', ARRAY['infraestrutura', 'coordenacao de infraestrutura', 'coordenacao infraestrutura'])
)
INSERT INTO sectors(name)
SELECT units.name FROM units
WHERE NOT EXISTS (
    SELECT 1 FROM sectors
    WHERE regexp_replace(
        lower(translate(trim(sectors.name),
            'ÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇáàâãäéèêëíìîïóòôõöúùûüç',
            'AAAAAEEEEIIIIOOOOOUUUUCaaaaaeeeeiiiiooooouuuuc')),
        '\s+', ' ', 'g'
    ) = ANY(units.aliases)
)
ON CONFLICT DO NOTHING;
