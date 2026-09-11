export default function Refresher({ loadData }) {
  return (
    <div id="refresh">
      <button onClick={loadData}>Refresh</button>
    </div>
  );
}
