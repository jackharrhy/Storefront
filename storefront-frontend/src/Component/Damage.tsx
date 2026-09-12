export default function Damage({
  maxDurability,
  damage,
}: {
  maxDurability: number;
  damage: number;
}) {
  const damageNormalized = Math.max(
    0,
    Math.min(100, ((maxDurability - damage) / maxDurability) * 100),
  );

  return (
    <div className="damage" title={`${Math.round(damageNormalized)}% durability`}>
      <div
        className="health"
        style={{
          width: `${damageNormalized}%`,
          backgroundColor: `hsl(${damageNormalized * 1.2} 100% 50%)`,
        }}
      />
    </div>
  );
}
